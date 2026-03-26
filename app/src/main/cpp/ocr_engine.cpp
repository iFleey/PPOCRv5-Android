/*
 * Copyright (C) 2025-2026 Fleey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "ocr_engine.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <numeric>

#include "image_utils.h"
#include "litert_config.h"
#include "logging.h"

#define TAG "OcrEngine"

namespace ppocrv5 {

    namespace {

        constexpr int kWarmupIterations = 3;
        constexpr int kWarmupImageSize = 128;

        constexpr float kMinBoxArea = 100.0f;
        constexpr float kMinConfidenceThreshold = 0.0f;
        constexpr int kMaxBoxesPerFrame = 50;
        constexpr float kTinyImageDirectRecognitionThreshold = 0.72f;
        constexpr float kTinyImageFallbackRecognitionThreshold = 0.45f;
        constexpr int kTinyImageShortSideThreshold = 64;
        constexpr int kTinyImageMinProcessingShortSide = 96;
        constexpr int kTinyImageMaxUpscaleFactor = 4;
        constexpr int kRecInputHeight = 48;
        constexpr int kRecInputWidth = 320;
        constexpr int kRecognitionWidthBucket = 32;

        int GetFallbackStartIndex(AcceleratorType requested) {
            switch (requested) {
                case AcceleratorType::kNpu:
                    return 0;
                case AcceleratorType::kGpu:
                    return 1;
                case AcceleratorType::kCpu:
                default:
                    return 2;
            }
        }

        constexpr std::array<AcceleratorType, 3> kAcceleratorCandidates = {
                AcceleratorType::kNpu,
                AcceleratorType::kGpu,
                AcceleratorType::kCpu,
        };

        const char *AcceleratorName(AcceleratorType type) {
            switch (type) {
                case AcceleratorType::kNpu:
                    return "NPU";
                case AcceleratorType::kGpu:
                    return "GPU";
                case AcceleratorType::kCpu:
                default:
                    return "CPU";
            }
        }

        inline void SortTopBoxesByArea(std::vector<RotatedRect> &boxes,
                                       std::vector<size_t> &indices,
                                       size_t limit) {
            indices.resize(boxes.size());
            std::iota(indices.begin(), indices.end(), 0);

            auto comparator = [&boxes](size_t a, size_t b) {
                return boxes[a].width * boxes[a].height > boxes[b].width * boxes[b].height;
            };

            if (indices.size() <= limit) {
                std::sort(indices.begin(), indices.end(), comparator);
                return;
            }

            auto middle = indices.begin() + static_cast<std::ptrdiff_t>(limit);
            std::partial_sort(indices.begin(), middle, indices.end(), comparator);
            indices.resize(limit);
        }

        bool ShouldUseTinyImagePath(int width, int height) {
            const int short_side = std::min(width, height);
            const int long_side = std::max(width, height);
            const float aspect_ratio = static_cast<float>(long_side) / std::max(short_side, 1);
            return short_side <= kTinyImageShortSideThreshold ||
                   (short_side <= 80 && aspect_ratio >= 2.2f) ||
                   (width * height <= 160 * 80);
        }

        int ComputeTinyImageUpscaleFactor(int width, int height) {
            const int short_side = std::max(1, std::min(width, height));
            const int factor = static_cast<int>(std::ceil(
                    static_cast<float>(kTinyImageMinProcessingShortSide) / short_side));
            return std::clamp(factor, 1, kTinyImageMaxUpscaleFactor);
        }

        bool IsEffectivelyFullImageBox(const RotatedRect &box, int width, int height) {
            const float image_area = static_cast<float>(width * height);
            if (image_area <= 0.0f) return false;

            const float box_area = box.width * box.height;
            if (box_area / image_area < 0.8f) return false;

            const float center_dx = std::abs(box.center_x - (width / 2.0f));
            const float center_dy = std::abs(box.center_y - (height / 2.0f));
            return center_dx <= width * 0.1f && center_dy <= height * 0.1f;
        }

        void UpscaleImageRgba(const uint8_t *image_data,
                             int width, int height, int stride,
                             int factor,
                             std::vector<uint8_t> *buffer,
                             int *out_width, int *out_height, int *out_stride) {
            const int scaled_width = width * factor;
            const int scaled_height = height * factor;
            buffer->resize(static_cast<size_t>(scaled_width) * scaled_height * 4);
            image_utils::ResizeBilinear(
                    image_data, width, height, stride,
                    buffer->data(), scaled_width, scaled_height);
            *out_width = scaled_width;
            *out_height = scaled_height;
            *out_stride = scaled_width * 4;
        }

        RotatedRect MakeFullImageBox(int width, int height) {
            RotatedRect box;
            box.center_x = width / 2.0f;
            box.center_y = height / 2.0f;
            box.width = static_cast<float>(width);
            box.height = static_cast<float>(height);
            box.angle = 0.0f;
            box.confidence = 1.0f;
            return box;
        }

        void ScaleBoxes(std::vector<RotatedRect> &boxes, float scale_factor) {
            for (auto &box: boxes) {
                box.center_x *= scale_factor;
                box.center_y *= scale_factor;
                box.width *= scale_factor;
                box.height *= scale_factor;
            }
        }

        int EstimateRecognitionTargetWidth(const RotatedRect &box) {
            float src_width = box.width;
            float src_height = box.height;
            if (src_width < src_height) {
                std::swap(src_width, src_height);
            }

            const float aspect_ratio = src_width / std::max(src_height, 1.0f);
            return std::clamp(static_cast<int>(kRecInputHeight * aspect_ratio), 1, kRecInputWidth);
        }

        void BucketRecognitionOrder(const std::vector<RotatedRect> &boxes,
                                    std::vector<size_t> &indices) {
            std::stable_sort(indices.begin(), indices.end(),
                             [&boxes](size_t a, size_t b) {
                                 const int width_a = EstimateRecognitionTargetWidth(boxes[a]);
                                 const int width_b = EstimateRecognitionTargetWidth(boxes[b]);
                                 const int bucket_a = width_a / kRecognitionWidthBucket;
                                 const int bucket_b = width_b / kRecognitionWidthBucket;
                                 if (bucket_a != bucket_b) {
                                     return bucket_a < bucket_b;
                                 }
                                 return width_a < width_b;
                             });
        }

        void SortResultsByReadingOrder(std::vector<OcrResult> &results) {
            std::sort(results.begin(), results.end(), [](const OcrResult &a, const OcrResult &b) {
                constexpr float kLineThreshold = 20.0f;
                if (std::abs(a.box.center_y - b.box.center_y) < kLineThreshold) {
                    return a.box.center_x < b.box.center_x;
                }
                return a.box.center_y < b.box.center_y;
            });
        }

    }  // namespace

    std::unique_ptr<OcrEngine> OcrEngine::Create(
            const std::string &det_model_path,
            const std::string &rec_model_path,
            const std::string &keys_path,
            AcceleratorType accelerator_type) {

        auto engine = std::unique_ptr<OcrEngine>(new OcrEngine());
        int start_index = GetFallbackStartIndex(accelerator_type);

        for (size_t i = start_index; i < kAcceleratorCandidates.size(); ++i) {
            AcceleratorType current_accelerator = kAcceleratorCandidates[i];
            LOGD(TAG, "Attempting to initialize with %s accelerator",
                 AcceleratorName(current_accelerator));

            auto detector = TextDetector::Create(det_model_path, current_accelerator);
            if (!detector) {
                LOGD(TAG, "TextDetector failed with %s, trying next",
                     AcceleratorName(current_accelerator));
                continue;
            }

            auto recognizer = TextRecognizer::Create(rec_model_path, keys_path, current_accelerator);
            if (!recognizer) {
                LOGD(TAG, "TextRecognizer failed with %s, trying next",
                     AcceleratorName(current_accelerator));
                continue;
            }

            engine->detector_ = std::move(detector);
            engine->recognizer_ = std::move(recognizer);
            engine->active_accelerator_ = current_accelerator;

            LOGD(TAG, "OcrEngine initialized with %s accelerator",
                 AcceleratorName(current_accelerator));

            engine->WarmUp();
            return engine;
        }

        LOGE(TAG, "Failed to initialize OcrEngine with any accelerator");
        return nullptr;
    }

    const std::vector<OcrResult> &OcrEngine::ProcessView(const uint8_t *image_data,
                                                         int width, int height, int stride) {
        results_buffer_.clear();

        if (!detector_ || !recognizer_) {
            LOGE(TAG, "OcrEngine not properly initialized");
            return results_buffer_;
        }

        auto total_start = std::chrono::high_resolution_clock::now();

        const uint8_t *processing_image_data = image_data;
        int processing_width = width;
        int processing_height = height;
        int processing_stride = stride;
        float processing_to_original_scale = 1.0f;
        const bool use_tiny_image_path = ShouldUseTinyImagePath(width, height);

        RecognitionResult direct_recognition_result;
        float direct_recognition_time_ms = 0.0f;
        bool has_direct_recognition_candidate = false;

        if (use_tiny_image_path) {
            const int upscale_factor = ComputeTinyImageUpscaleFactor(width, height);
            if (upscale_factor > 1) {
                UpscaleImageRgba(
                        image_data, width, height, stride, upscale_factor,
                        &upscale_buffer_,
                        &processing_width, &processing_height, &processing_stride);
                processing_image_data = upscale_buffer_.data();
                processing_to_original_scale = 1.0f / upscale_factor;
            }

            direct_recognition_result = recognizer_->Recognize(
                    processing_image_data,
                    processing_width,
                    processing_height,
                    processing_stride,
                    MakeFullImageBox(processing_width, processing_height),
                    &direct_recognition_time_ms);
            has_direct_recognition_candidate = !direct_recognition_result.text.empty();

            if (has_direct_recognition_candidate &&
                direct_recognition_result.confidence >= kTinyImageDirectRecognitionThreshold) {
                OcrResult result;
                result.text = std::move(direct_recognition_result.text);
                result.confidence = direct_recognition_result.confidence;
                result.box = MakeFullImageBox(width, height);
                results_buffer_.push_back(std::move(result));

                benchmark_.detection_time_ms = 0.0f;
                benchmark_.recognition_time_ms = direct_recognition_time_ms;
                benchmark_.total_time_ms = direct_recognition_time_ms;
                benchmark_.fps = (benchmark_.total_time_ms > 0.0f) ? (1000.0f / benchmark_.total_time_ms) : 0.0f;
                return results_buffer_;
            }
        }

        float detection_time_ms = 0.0f;
        const auto &boxes = detector_->DetectView(
                processing_image_data,
                processing_width,
                processing_height,
                processing_stride,
                &detection_time_ms);
        benchmark_.detection_time_ms = detection_time_ms;

        if (boxes.empty()) {
            if (has_direct_recognition_candidate &&
                direct_recognition_result.confidence >= kTinyImageFallbackRecognitionThreshold) {
                OcrResult result;
                result.text = std::move(direct_recognition_result.text);
                result.confidence = direct_recognition_result.confidence;
                result.box = MakeFullImageBox(width, height);
                results_buffer_.push_back(std::move(result));

                auto total_end = std::chrono::high_resolution_clock::now();
                benchmark_.recognition_time_ms = direct_recognition_time_ms;
                benchmark_.total_time_ms = std::chrono::duration_cast<std::chrono::microseconds>(
                        total_end - total_start).count() / 1000.0f;
                benchmark_.fps = (benchmark_.total_time_ms > 0.0f) ? (1000.0f / benchmark_.total_time_ms) : 0.0f;
                return results_buffer_;
            }

            auto total_end = std::chrono::high_resolution_clock::now();
            benchmark_.total_time_ms = std::chrono::duration_cast<std::chrono::microseconds>(
                    total_end - total_start).count() / 1000.0f;
            benchmark_.recognition_time_ms = 0.0f;
            benchmark_.fps = (benchmark_.total_time_ms > 0.0f) ? (1000.0f / benchmark_.total_time_ms) : 0.0f;
            return results_buffer_;
        }

        filtered_boxes_buffer_.clear();
        filtered_boxes_buffer_.reserve(boxes.size());
        const float min_box_area = use_tiny_image_path ? 16.0f : kMinBoxArea;

        for (const auto &box: boxes) {
            RotatedRect scaled_box = box;
            if (processing_to_original_scale != 1.0f) {
                scaled_box.center_x *= processing_to_original_scale;
                scaled_box.center_y *= processing_to_original_scale;
                scaled_box.width *= processing_to_original_scale;
                scaled_box.height *= processing_to_original_scale;
            }

            if (scaled_box.width * scaled_box.height >= min_box_area) {
                filtered_boxes_buffer_.push_back(scaled_box);
            }
        }

        if (filtered_boxes_buffer_.empty()) {
            if (has_direct_recognition_candidate &&
                direct_recognition_result.confidence >= kTinyImageFallbackRecognitionThreshold) {
                OcrResult result;
                result.text = std::move(direct_recognition_result.text);
                result.confidence = direct_recognition_result.confidence;
                result.box = MakeFullImageBox(width, height);
                results_buffer_.push_back(std::move(result));

                auto total_end = std::chrono::high_resolution_clock::now();
                benchmark_.recognition_time_ms = direct_recognition_time_ms;
                benchmark_.total_time_ms = std::chrono::duration_cast<std::chrono::microseconds>(
                        total_end - total_start).count() / 1000.0f;
                benchmark_.fps = (benchmark_.total_time_ms > 0.0f) ? (1000.0f / benchmark_.total_time_ms) : 0.0f;
                return results_buffer_;
            }

            auto total_end = std::chrono::high_resolution_clock::now();
            benchmark_.total_time_ms = std::chrono::duration_cast<std::chrono::microseconds>(
                    total_end - total_start).count() / 1000.0f;
            benchmark_.recognition_time_ms = 0.0f;
            benchmark_.fps = (benchmark_.total_time_ms > 0.0f) ? (1000.0f / benchmark_.total_time_ms) : 0.0f;
            return results_buffer_;
        }

        SortTopBoxesByArea(filtered_boxes_buffer_, sorted_indices_buffer_, kMaxBoxesPerFrame);
        recognition_order_buffer_ = sorted_indices_buffer_;
        BucketRecognitionOrder(filtered_boxes_buffer_, recognition_order_buffer_);

        if (use_tiny_image_path &&
            has_direct_recognition_candidate &&
            direct_recognition_result.confidence >= kTinyImageFallbackRecognitionThreshold &&
            sorted_indices_buffer_.size() == 1 &&
            IsEffectivelyFullImageBox(filtered_boxes_buffer_[sorted_indices_buffer_.front()], width, height)) {
            OcrResult result;
            result.text = std::move(direct_recognition_result.text);
            result.confidence = direct_recognition_result.confidence;
            result.box = filtered_boxes_buffer_[sorted_indices_buffer_.front()];
            results_buffer_.push_back(std::move(result));

            auto total_end = std::chrono::high_resolution_clock::now();
            benchmark_.recognition_time_ms = direct_recognition_time_ms;
            benchmark_.total_time_ms = std::chrono::duration_cast<std::chrono::microseconds>(
                    total_end - total_start).count() / 1000.0f;
            benchmark_.fps = (benchmark_.total_time_ms > 0.0f) ? (1000.0f / benchmark_.total_time_ms) : 0.0f;
            return results_buffer_;
        }

        results_buffer_.reserve(recognition_order_buffer_.size());

        auto rec_start = std::chrono::high_resolution_clock::now();

        for (size_t idx: recognition_order_buffer_) {
            const auto &box = filtered_boxes_buffer_[idx];
            float rec_time_ms = 0.0f;
            RotatedRect recognition_box = box;
            if (processing_to_original_scale != 1.0f) {
                recognition_box.center_x /= processing_to_original_scale;
                recognition_box.center_y /= processing_to_original_scale;
                recognition_box.width /= processing_to_original_scale;
                recognition_box.height /= processing_to_original_scale;
            }

            auto rec_result = recognizer_->Recognize(processing_image_data,
                                                     processing_width,
                                                     processing_height,
                                                     processing_stride,
                                                     recognition_box,
                                                     &rec_time_ms);

            if (!rec_result.text.empty() && rec_result.confidence >= kMinConfidenceThreshold) {
                OcrResult result;
                result.text = std::move(rec_result.text);
                result.confidence = rec_result.confidence;
                result.box = box;
                results_buffer_.push_back(std::move(result));
            }
        }

        auto rec_end = std::chrono::high_resolution_clock::now();
        benchmark_.recognition_time_ms = std::chrono::duration_cast<std::chrono::microseconds>(
                rec_end - rec_start).count() / 1000.0f;

        auto total_end = std::chrono::high_resolution_clock::now();
        benchmark_.total_time_ms = std::chrono::duration_cast<std::chrono::microseconds>(
                total_end - total_start).count() / 1000.0f;
        benchmark_.fps = (benchmark_.total_time_ms > 0.0f) ? (1000.0f / benchmark_.total_time_ms) : 0.0f;
        SortResultsByReadingOrder(results_buffer_);

        LOGD(TAG, "OCR: %zu/%zu results, det=%.1fms, rec=%.1fms (%.1fms/box), total=%.1fms",
             results_buffer_.size(), filtered_boxes_buffer_.size(),
             benchmark_.detection_time_ms, benchmark_.recognition_time_ms,
             filtered_boxes_buffer_.size() > 0
             ? benchmark_.recognition_time_ms / filtered_boxes_buffer_.size()
             : 0.0f,
             benchmark_.total_time_ms);

        return results_buffer_;
    }

    std::vector<OcrResult> OcrEngine::Process(const uint8_t *image_data,
                                              int width, int height, int stride) {
        return ProcessView(image_data, width, height, stride);
    }

    Benchmark OcrEngine::GetBenchmark() const {
        return benchmark_;
    }

    AcceleratorType OcrEngine::GetActiveAccelerator() const {
        return active_accelerator_;
    }

    void OcrEngine::WarmUp() {
        LOGD(TAG, "Starting warm-up (%d iterations)...", kWarmupIterations);

        std::vector<uint8_t> dummy_image(kWarmupImageSize * kWarmupImageSize * 4, 128);
        for (int i = 0; i < kWarmupImageSize * kWarmupImageSize; ++i) {
            dummy_image[i * 4 + 0] = static_cast<uint8_t>((i * 7) % 256);
            dummy_image[i * 4 + 1] = static_cast<uint8_t>((i * 11) % 256);
            dummy_image[i * 4 + 2] = static_cast<uint8_t>((i * 13) % 256);
            dummy_image[i * 4 + 3] = 255;
        }

        for (int iter = 0; iter < kWarmupIterations; ++iter) {
            float detection_time_ms = 0.0f;
            detector_->Detect(dummy_image.data(), kWarmupImageSize, kWarmupImageSize,
                              kWarmupImageSize * 4, &detection_time_ms);

            float recognition_time_ms = 0.0f;
            recognizer_->Recognize(dummy_image.data(), kWarmupImageSize, kWarmupImageSize,
                                   kWarmupImageSize * 4,
                                   MakeFullImageBox(kWarmupImageSize, kWarmupImageSize),
                                   &recognition_time_ms);
        }

        LOGD(TAG, "Warm-up completed (accelerator: %s)", AcceleratorName(active_accelerator_));
    }

}  // namespace ppocrv5
