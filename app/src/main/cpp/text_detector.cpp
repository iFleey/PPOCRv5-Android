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

#include "text_detector.h"

#include <chrono>
#include <cmath>
#include <cstring>
#include <vector>

#include "image_utils.h"
#include "litert_config.h"
#include "logging.h"
#include "ocr_engine.h"
#include "postprocess.h"

// LiteRT C++ API headers
#include "litert/cc/litert_compiled_model.h"
#include "litert/cc/litert_environment.h"
#include "litert/cc/litert_expected.h"
#include "litert/cc/litert_options.h"
#include "litert/cc/litert_tensor_buffer.h"

// LiteRT C API headers for direct buffer creation
#include "litert/c/litert_compiled_model.h"
#include "litert/c/litert_model_types.h"
#include "litert/c/litert_tensor_buffer.h"
#include "litert/c/litert_tensor_buffer_requirements.h"

#if defined(__ARM_NEON) || defined(__ARM_NEON__)

#include <arm_neon.h>

#endif

#define TAG "TextDetector"

namespace ppocrv5 {

    namespace {

        constexpr int kDetInputSize = 640;
        constexpr float kBinaryThreshold = 0.1f;
        constexpr float kBoxThreshold = 0.3f;
        constexpr float kMinBoxArea = 50.0f;
        constexpr float kUnclipRatio = 1.5f;

        litert::HwAccelerators ToLiteRtAccelerator(AcceleratorType type) {
            switch (type) {
                case AcceleratorType::kGpu:
                    return litert::HwAccelerators::kGpu;
                case AcceleratorType::kNpu:
                    return litert::HwAccelerators::kNpu;
                case AcceleratorType::kCpu:
                default:
                    return litert::HwAccelerators::kCpu;
            }
        }

        void UnclipBox(RotatedRect &box, float unclip_ratio) {
            float area = box.width * box.height;
            float perimeter = 2.0f * (box.width + box.height);
            if (perimeter < 1e-6f) return;

            float distance = area * unclip_ratio / perimeter;
            box.width += 2.0f * distance;
            box.height += 2.0f * distance;
        }

    }  // namespace

    class TextDetector::Impl {
    public:
        // LiteRT C++ API objects
        std::optional<litert::Environment> env_;
        std::optional<litert::CompiledModel> compiled_model_;
        std::vector<litert::TensorBuffer> input_buffers_;
        std::vector<litert::TensorBuffer> output_buffers_;

        image_utils::LetterboxInfo letterbox_info_;

        // Pre-allocated buffers with cache-line alignment
        alignas(64) std::vector<uint8_t> resized_buffer_;
        alignas(64) std::vector<float> normalized_buffer_;
        alignas(64) std::vector<uint8_t> binary_map_;
        alignas(64) std::vector<float> prob_map_;
        alignas(64) std::vector<float> box_score_sum_integral_;
        alignas(64) std::vector<uint32_t> box_score_count_integral_;
        postprocess::ContourScratch contour_scratch_;
        std::vector<RotatedRect> boxes_buffer_;
        std::vector<RotatedRect> filtered_boxes_buffer_;

        // Quantization parameters
        bool input_is_int8_ = false;
        bool input_is_uint8_ = false;
        bool input_is_quantized_ = false;
        float input_scale_ = 1.0f / 255.0f;
        int input_zero_point_ = 0;

        bool output_is_int8_ = false;
        bool output_is_uint8_ = false;
        bool output_is_quantized_ = false;
        float output_scale_ = 1.0f / 255.0f;
        int output_zero_point_ = 0;

        ~Impl() = default;

        bool Initialize(const std::string &model_path, AcceleratorType accelerator_type) {
            auto env_result = litert::Environment::Create({});
            if (!env_result) {
                LOGE(TAG, "Failed to create LiteRT environment: %s",
                     env_result.Error().Message().c_str());
                return false;
            }
            env_ = std::move(*env_result);
            LOGD(TAG, "LiteRT C++ Environment created successfully");

            auto options_result = litert::Options::Create();
            if (!options_result) {
                LOGE(TAG, "Failed to create options: %s",
                     options_result.Error().Message().c_str());
                return false;
            }
            auto &options = *options_result;

            auto hw_accelerator = ToLiteRtAccelerator(accelerator_type);
            auto set_result = options.SetHardwareAccelerators(hw_accelerator);
            if (!set_result) {
                LOGE(TAG, "Failed to set hardware accelerators: %s",
                     set_result.Error().Message().c_str());
                return false;
            }

            auto model_result = litert::CompiledModel::Create(*env_, model_path, options);
            if (!model_result) {
                LOGW(TAG, "Failed to create CompiledModel with accelerator %d: %s",
                     static_cast<int>(accelerator_type),
                     model_result.Error().Message().c_str());
                return false;
            }
            compiled_model_ = std::move(*model_result);
            LOGD(TAG, "CompiledModel created successfully with C++ API");

            std::vector<int> input_dims = {1, kDetInputSize, kDetInputSize, 3};
            auto resize_result = compiled_model_->ResizeInputTensor(0, absl::MakeConstSpan(input_dims));
            if (!resize_result) {
                LOGE(TAG, "Failed to resize input tensor: %s",
                     resize_result.Error().Message().c_str());
                return false;
            }

            auto input_type_result = compiled_model_->GetInputTensorType(/*signature_index=*/0, /*input_index=*/0);
            if (input_type_result) {
                auto &input_type = *input_type_result;
                auto element_type = input_type.ElementType();
                input_is_int8_ = (element_type == litert::ElementType::Int8);
                input_is_uint8_ = (element_type == litert::ElementType::UInt8);
                input_is_quantized_ = (input_is_int8_ || input_is_uint8_);
                LOGD(TAG, "Input tensor type: %s",
                     input_is_int8_ ? "INT8" : input_is_uint8_ ? "UINT8" : "FLOAT32");
            }

            auto output_type_result = compiled_model_->GetOutputTensorType(/*signature_index=*/0, /*output_index=*/0);
            if (output_type_result) {
                auto &output_type = *output_type_result;
                auto element_type = output_type.ElementType();
                output_is_int8_ = (element_type == litert::ElementType::Int8);
                output_is_uint8_ = (element_type == litert::ElementType::UInt8);
                output_is_quantized_ = (output_is_int8_ || output_is_uint8_);
                LOGD(TAG, "Output tensor type: %s",
                     output_is_int8_ ? "INT8" : output_is_uint8_ ? "UINT8" : "FLOAT32");
            }

            if (!CreateBuffersWithCApi()) {
                LOGE(TAG, "Failed to create input/output buffers");
                return false;
            }
            LOGD(TAG, "Created %zu input buffers, %zu output buffers",
                 input_buffers_.size(), output_buffers_.size());

            resized_buffer_.resize(kDetInputSize * kDetInputSize * 4);
            normalized_buffer_.resize(kDetInputSize * kDetInputSize * 3);
            binary_map_.resize(kDetInputSize * kDetInputSize);
            prob_map_.resize(kDetInputSize * kDetInputSize);
            box_score_sum_integral_.resize((kDetInputSize + 1) * (kDetInputSize + 1));
            box_score_count_integral_.resize((kDetInputSize + 1) * (kDetInputSize + 1));
            boxes_buffer_.reserve(128);
            filtered_boxes_buffer_.reserve(128);

            LOGD(TAG, "TextDetector initialized successfully with C++ API");
            return true;
        }

        bool CreateBuffersWithCApi() {
            if (!compiled_model_ || !env_) {
                LOGE(TAG, "CompiledModel or Environment not initialized");
                return false;
            }

            LiteRtCompiledModel c_model = compiled_model_->Get();
            LiteRtEnvironment c_env = env_->Get();

            LiteRtTensorBufferRequirements input_requirements = nullptr;
            auto status = LiteRtGetCompiledModelInputBufferRequirements(
                    c_model, /*signature_index=*/0, /*input_index=*/0, &input_requirements);
            if (status != kLiteRtStatusOk || input_requirements == nullptr) {
                LOGE(TAG, "Failed to get input buffer requirements: %d", status);
                return false;
            }

            auto input_type_result = compiled_model_->GetInputTensorType(/*signature_index=*/0, /*input_index=*/0);
            if (!input_type_result) {
                LOGE(TAG, "Failed to get input tensor type");
                return false;
            }
            LiteRtRankedTensorType input_tensor_type =
                    static_cast<LiteRtRankedTensorType>(*input_type_result);

            LiteRtTensorBuffer input_buffer = nullptr;
            status = LiteRtCreateManagedTensorBufferFromRequirements(
                    c_env, &input_tensor_type, input_requirements, &input_buffer);
            if (status != kLiteRtStatusOk || input_buffer == nullptr) {
                LOGE(TAG, "Failed to create input tensor buffer: %d", status);
                return false;
            }
            input_buffers_.push_back(litert::TensorBuffer::WrapCObject(input_buffer, litert::OwnHandle::kYes));
            LOGD(TAG, "Created input buffer successfully");

            LiteRtTensorBufferRequirements output_requirements = nullptr;
            status = LiteRtGetCompiledModelOutputBufferRequirements(
                    c_model, /*signature_index=*/0, /*output_index=*/0, &output_requirements);
            if (status != kLiteRtStatusOk || output_requirements == nullptr) {
                LOGE(TAG, "Failed to get output buffer requirements: %d", status);
                return false;
            }

            auto output_type_result = compiled_model_->GetOutputTensorType(/*signature_index=*/0, /*output_index=*/0);
            if (!output_type_result) {
                LOGE(TAG, "Failed to get output tensor type");
                return false;
            }
            LiteRtRankedTensorType output_tensor_type =
                    static_cast<LiteRtRankedTensorType>(*output_type_result);

            LiteRtTensorBuffer output_buffer = nullptr;
            status = LiteRtCreateManagedTensorBufferFromRequirements(
                    c_env, &output_tensor_type, output_requirements, &output_buffer);
            if (status != kLiteRtStatusOk || output_buffer == nullptr) {
                LOGE(TAG, "Failed to create output tensor buffer: %d", status);
                return false;
            }
            output_buffers_.push_back(litert::TensorBuffer::WrapCObject(output_buffer, litert::OwnHandle::kYes));
            LOGD(TAG, "Created output buffer successfully");

            return true;
        }

        const std::vector<RotatedRect> &DetectView(const uint8_t *image_data,
                                                   int width, int height, int stride,
                                                   float *detection_time_ms) {
            auto start_time = std::chrono::high_resolution_clock::now();

            if (input_is_quantized_) {
                image_utils::LetterboxResize(image_data, width, height, stride,
                                             resized_buffer_.data(), kDetInputSize, kDetInputSize,
                                             &letterbox_info_);
                PrepareQuantizedInput();
            } else {
                image_utils::LetterboxResizeNormalizeImageNet(
                        image_data, width, height, stride,
                        normalized_buffer_.data(), kDetInputSize, kDetInputSize,
                        &letterbox_info_);
            }

            auto write_result = WriteInputBuffer();
            if (!write_result) {
                LOGE(TAG, "Failed to write input buffer: %s",
                     write_result.Error().Message().c_str());
                if (detection_time_ms) *detection_time_ms = 0.0f;
                filtered_boxes_buffer_.clear();
                return filtered_boxes_buffer_;
            }

            LOGD(TAG, "Running inference with C++ API...");
            auto run_result = compiled_model_->Run(input_buffers_, output_buffers_);
            if (!run_result) {
                LOGE(TAG, "Failed to run inference: %s",
                     run_result.Error().Message().c_str());
                if (detection_time_ms) *detection_time_ms = 0.0f;
                filtered_boxes_buffer_.clear();
                return filtered_boxes_buffer_;
            }

            auto read_result = ReadOutputBuffer();
            if (!read_result) {
                LOGE(TAG, "Failed to read output buffer: %s",
                     read_result.Error().Message().c_str());
                if (detection_time_ms) *detection_time_ms = 0.0f;
                filtered_boxes_buffer_.clear();
                return filtered_boxes_buffer_;
            }

            const int total_pixels = kDetInputSize * kDetInputSize;
            float *prob_map = prob_map_.data();

#ifndef NDEBUG
            float min_val = prob_map[0], max_val = prob_map[0];
            float sum_val = 0.0f;
            int above_threshold = 0;
            for (int i = 0; i < total_pixels; ++i) {
                float v = prob_map[i];
                min_val = std::min(min_val, v);
                max_val = std::max(max_val, v);
                sum_val += v;
                if (v > kBinaryThreshold) above_threshold++;
            }
            LOGD(TAG, "Prob map stats: min=%.4f, max=%.4f, mean=%.6f, above_threshold=%d",
                 min_val, max_val, sum_val / total_pixels, above_threshold);
#endif

            BinarizeOutput(prob_map, total_pixels);
            BuildBoxScoreIntegral(prob_map);

#ifndef NDEBUG
            auto postprocess_start = std::chrono::high_resolution_clock::now();
#endif
            postprocess::FindContours(binary_map_.data(),
                                      kDetInputSize, kDetInputSize,
                                      &contour_scratch_);
            const auto &contours = contour_scratch_.contours;
#ifndef NDEBUG
            auto contours_end = std::chrono::high_resolution_clock::now();
            auto contours_duration = std::chrono::duration_cast<std::chrono::microseconds>(
                    contours_end - postprocess_start);
            LOGD(TAG, "FindContours: %zu contours in %.2f ms",
                 contours.size(), contours_duration.count() / 1000.0f);
#endif

            boxes_buffer_.clear();
            boxes_buffer_.reserve(contours.size());

#ifndef NDEBUG
            int skipped_small_contour = 0;
            int skipped_small_rect = 0;
            int skipped_low_score = 0;
#endif

            for (const auto &contour: contours) {
                if (contour.size < 4) {
#ifndef NDEBUG
                    skipped_small_contour++;
#endif
                    continue;
                }

                RotatedRect rect = postprocess::MinAreaRect(contour, &contour_scratch_);
                if (rect.width < 1.0f || rect.height < 1.0f) {
#ifndef NDEBUG
                    skipped_small_rect++;
#endif
                    continue;
                }

                float box_score = CalculateBoxScore(contour);
                if (box_score < kBoxThreshold) {
#ifndef NDEBUG
                    skipped_low_score++;
#endif
                    continue;
                }

                UnclipBox(rect, kUnclipRatio);

                rect.center_x = (rect.center_x - letterbox_info_.pad_x) / letterbox_info_.scale;
                rect.center_y = (rect.center_y - letterbox_info_.pad_y) / letterbox_info_.scale;
                rect.width /= letterbox_info_.scale;
                rect.height /= letterbox_info_.scale;
                rect.confidence = box_score;

                if (rect.center_x < 0.0f || rect.center_x > width ||
                    rect.center_y < 0.0f || rect.center_y > height) {
                    continue;
                }

                boxes_buffer_.push_back(rect);
            }

#ifndef NDEBUG
            LOGD(TAG, "Box filtering: skipped_small_contour=%d, skipped_small_rect=%d, "
                      "skipped_low_score=%d, passed=%zu",
                 skipped_small_contour, skipped_small_rect, skipped_low_score, boxes_buffer_.size());
#endif

            postprocess::FilterAndSortBoxes(boxes_buffer_, kBoxThreshold, kMinBoxArea,
                                            &filtered_boxes_buffer_);

            auto end_time = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::microseconds>(
                    end_time - start_time);
            if (detection_time_ms) {
                *detection_time_ms = duration.count() / 1000.0f;
            }

            LOGD(TAG, "Detection completed: %zu boxes in %.2f ms",
                 filtered_boxes_buffer_.size(), duration.count() / 1000.0f);

            return filtered_boxes_buffer_;
        }

    private:
        void PrepareQuantizedInput() {
            constexpr float kMeanR = 0.485f, kMeanG = 0.456f, kMeanB = 0.406f;
            constexpr float kStdR = 0.229f, kStdG = 0.224f, kStdB = 0.225f;

            const uint8_t *src = resized_buffer_.data();
            const float inv_scale = 1.0f / input_scale_;

            if (input_is_int8_) {
                int8_t *dst = reinterpret_cast<int8_t *>(normalized_buffer_.data());
                for (int i = 0; i < kDetInputSize * kDetInputSize; ++i) {
                    float r = src[i * 4 + 0] / 255.0f;
                    float g = src[i * 4 + 1] / 255.0f;
                    float b = src[i * 4 + 2] / 255.0f;

                    float norm_r = (r - kMeanR) / kStdR;
                    float norm_g = (g - kMeanG) / kStdG;
                    float norm_b = (b - kMeanB) / kStdB;

                    int q_r = static_cast<int>(std::round(norm_r * inv_scale)) + input_zero_point_;
                    int q_g = static_cast<int>(std::round(norm_g * inv_scale)) + input_zero_point_;
                    int q_b = static_cast<int>(std::round(norm_b * inv_scale)) + input_zero_point_;

                    dst[i * 3 + 0] = static_cast<int8_t>(std::max(-128, std::min(127, q_r)));
                    dst[i * 3 + 1] = static_cast<int8_t>(std::max(-128, std::min(127, q_g)));
                    dst[i * 3 + 2] = static_cast<int8_t>(std::max(-128, std::min(127, q_b)));
                }
            } else {
                uint8_t *dst = reinterpret_cast<uint8_t *>(normalized_buffer_.data());
                for (int i = 0; i < kDetInputSize * kDetInputSize; ++i) {
                    float r = src[i * 4 + 0] / 255.0f;
                    float g = src[i * 4 + 1] / 255.0f;
                    float b = src[i * 4 + 2] / 255.0f;

                    float norm_r = (r - kMeanR) / kStdR;
                    float norm_g = (g - kMeanG) / kStdG;
                    float norm_b = (b - kMeanB) / kStdB;

                    int q_r = static_cast<int>(std::round(norm_r * inv_scale)) + input_zero_point_;
                    int q_g = static_cast<int>(std::round(norm_g * inv_scale)) + input_zero_point_;
                    int q_b = static_cast<int>(std::round(norm_b * inv_scale)) + input_zero_point_;

                    dst[i * 3 + 0] = static_cast<uint8_t>(std::max(0, std::min(255, q_r)));
                    dst[i * 3 + 1] = static_cast<uint8_t>(std::max(0, std::min(255, q_g)));
                    dst[i * 3 + 2] = static_cast<uint8_t>(std::max(0, std::min(255, q_b)));
                }
            }
        }

        void PrepareFloatInput() {
            image_utils::NormalizeImageNet(resized_buffer_.data(),
                                           kDetInputSize, kDetInputSize,
                                           kDetInputSize * 4,
                                           normalized_buffer_.data());
        }

        litert::Expected<void> WriteInputBuffer() {
            if (input_buffers_.empty()) {
                return litert::Unexpected(kLiteRtStatusErrorInvalidArgument,
                                          "No input buffers available");
            }

            if (input_is_quantized_) {
                size_t data_size = kDetInputSize * kDetInputSize * 3;
                if (input_is_int8_) {
                    return input_buffers_[0].Write<int8_t>(
                            absl::MakeConstSpan(reinterpret_cast<const int8_t *>(normalized_buffer_.data()),
                                                data_size));
                } else {
                    return input_buffers_[0].Write<uint8_t>(
                            absl::MakeConstSpan(reinterpret_cast<const uint8_t *>(normalized_buffer_.data()),
                                                data_size));
                }
            } else {
                return input_buffers_[0].Write<float>(
                        absl::MakeConstSpan(normalized_buffer_.data(), normalized_buffer_.size()));
            }
        }

        litert::Expected<void> ReadOutputBuffer() {
            if (output_buffers_.empty()) {
                return litert::Unexpected(kLiteRtStatusErrorInvalidArgument,
                                          "No output buffers available");
            }

            const int total_pixels = kDetInputSize * kDetInputSize;
            float *prob_map = prob_map_.data();

            if (output_is_quantized_) {
                if (output_is_int8_) {
                    std::vector<int8_t> int8_output(total_pixels);
                    auto read_result = output_buffers_[0].Read<int8_t>(
                            absl::MakeSpan(int8_output.data(), total_pixels));
                    if (!read_result) return read_result;

                    for (int i = 0; i < total_pixels; ++i) {
                        prob_map[i] = (static_cast<float>(int8_output[i]) - output_zero_point_) * output_scale_;
                    }
                } else {
                    std::vector<uint8_t> uint8_output(total_pixels);
                    auto read_result = output_buffers_[0].Read<uint8_t>(
                            absl::MakeSpan(uint8_output.data(), total_pixels));
                    if (!read_result) return read_result;

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
                    const float32x4_t v_scale = vdupq_n_f32(output_scale_);
                    const float32x4_t v_zp = vdupq_n_f32(static_cast<float>(output_zero_point_));

                    int i = 0;
                    for (; i + 8 <= total_pixels; i += 8) {
                        uint8x8_t u8_vals = vld1_u8(uint8_output.data() + i);
                        uint16x8_t u16_vals = vmovl_u8(u8_vals);
                        uint32x4_t u32_lo = vmovl_u16(vget_low_u16(u16_vals));
                        uint32x4_t u32_hi = vmovl_u16(vget_high_u16(u16_vals));
                        float32x4_t f32_lo = vmulq_f32(vsubq_f32(vcvtq_f32_u32(u32_lo), v_zp), v_scale);
                        float32x4_t f32_hi = vmulq_f32(vsubq_f32(vcvtq_f32_u32(u32_hi), v_zp), v_scale);
                        vst1q_f32(prob_map + i, f32_lo);
                        vst1q_f32(prob_map + i + 4, f32_hi);
                    }
                    for (; i < total_pixels; ++i) {
                        prob_map[i] = (static_cast<float>(uint8_output[i]) - output_zero_point_) * output_scale_;
                    }
#else
                    for (int i = 0; i < total_pixels; ++i) {
                        prob_map[i] = (static_cast<float>(uint8_output[i]) - output_zero_point_) * output_scale_;
                    }
#endif
                }
            } else {
                auto read_result = output_buffers_[0].Read<float>(
                        absl::MakeSpan(prob_map, total_pixels));
                if (!read_result) return read_result;

                float raw_min = prob_map[0], raw_max = prob_map[0];
                for (int i = 1; i < total_pixels; ++i) {
                    raw_min = std::min(raw_min, prob_map[i]);
                    raw_max = std::max(raw_max, prob_map[i]);
                }
#ifndef NDEBUG
                LOGD(TAG, "Raw FLOAT32 output range: min=%.4f, max=%.4f", raw_min, raw_max);
#endif

                bool need_sigmoid = (raw_min < -0.5f || raw_max > 1.5f);
                if (need_sigmoid) {
                    LOGD(TAG, "Applying sigmoid activation");
                    ApplySigmoid(prob_map, total_pixels);
                }
            }

            return {};
        }

        void ApplySigmoid(float *data, int size) {
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
            int i = 0;
            for (; i + 4 <= size; i += 4) {
                float vals[4];
                vst1q_f32(vals, vld1q_f32(data + i));
                for (int j = 0; j < 4; ++j) {
                    vals[j] = std::max(-10.0f, std::min(10.0f, vals[j]));
                    vals[j] = 1.0f / (1.0f + std::exp(-vals[j]));
                }
                vst1q_f32(data + i, vld1q_f32(vals));
            }
            for (; i < size; ++i) {
                data[i] = 1.0f / (1.0f + std::exp(-data[i]));
            }
#else
            for (int i = 0; i < size; ++i) {
                data[i] = 1.0f / (1.0f + std::exp(-data[i]));
            }
#endif
        }

        void BinarizeOutput(const float *prob_map, int total_pixels) {
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
            const float32x4_t v_threshold = vdupq_n_f32(kBinaryThreshold);
            const uint8x16_t v_255 = vdupq_n_u8(255);
            const uint8x16_t v_0 = vdupq_n_u8(0);

            int i = 0;
            for (; i + 16 <= total_pixels; i += 16) {
                float32x4_t f0 = vld1q_f32(prob_map + i);
                float32x4_t f1 = vld1q_f32(prob_map + i + 4);
                float32x4_t f2 = vld1q_f32(prob_map + i + 8);
                float32x4_t f3 = vld1q_f32(prob_map + i + 12);

                uint32x4_t cmp0 = vcgtq_f32(f0, v_threshold);
                uint32x4_t cmp1 = vcgtq_f32(f1, v_threshold);
                uint32x4_t cmp2 = vcgtq_f32(f2, v_threshold);
                uint32x4_t cmp3 = vcgtq_f32(f3, v_threshold);

                uint16x4_t n0 = vmovn_u32(cmp0);
                uint16x4_t n1 = vmovn_u32(cmp1);
                uint16x4_t n2 = vmovn_u32(cmp2);
                uint16x4_t n3 = vmovn_u32(cmp3);
                uint16x8_t n01 = vcombine_u16(n0, n1);
                uint16x8_t n23 = vcombine_u16(n2, n3);

                uint8x8_t b01 = vmovn_u16(n01);
                uint8x8_t b23 = vmovn_u16(n23);
                uint8x16_t result = vcombine_u8(b01, b23);
                result = vbslq_u8(result, v_255, v_0);

                vst1q_u8(binary_map_.data() + i, result);
            }
            for (; i < total_pixels; ++i) {
                binary_map_[i] = (prob_map[i] > kBinaryThreshold) ? 255 : 0;
            }
#else
            for (int i = 0; i < total_pixels; ++i) {
                binary_map_[i] = (prob_map[i] > kBinaryThreshold) ? 255 : 0;
            }
#endif

#ifndef NDEBUG
            int binary_nonzero = 0;
            for (int i = 0; i < total_pixels; ++i) {
                if (binary_map_[i] > 0) binary_nonzero++;
            }
            LOGD(TAG, "Binary map non-zero pixels: %d", binary_nonzero);
#endif
        }

        void BuildBoxScoreIntegral(const float *prob_map) {
            constexpr int kIntegralStride = kDetInputSize + 1;

            std::fill_n(box_score_sum_integral_.data(), kIntegralStride, 0.0f);
            std::fill_n(box_score_count_integral_.data(), kIntegralStride, 0u);

            for (int y = 0; y < kDetInputSize; ++y) {
                const size_t src_row_offset = static_cast<size_t>(y) * kDetInputSize;
                const uint8_t *binary_row = binary_map_.data() + src_row_offset;
                const float *prob_row = prob_map + src_row_offset;

                const size_t prev_row_offset = static_cast<size_t>(y) * kIntegralStride;
                const size_t dst_row_offset = static_cast<size_t>(y + 1) * kIntegralStride;
                box_score_sum_integral_[dst_row_offset] = 0.0f;
                box_score_count_integral_[dst_row_offset] = 0u;

                float row_sum = 0.0f;
                uint32_t row_count = 0;
                for (int x = 0; x < kDetInputSize; ++x) {
                    const uint32_t mask = binary_row[x] > 0 ? 1u : 0u;
                    row_sum += mask ? prob_row[x] : 0.0f;
                    row_count += mask;
                    box_score_sum_integral_[dst_row_offset + x + 1] =
                            box_score_sum_integral_[prev_row_offset + x + 1] + row_sum;
                    box_score_count_integral_[dst_row_offset + x + 1] =
                            box_score_count_integral_[prev_row_offset + x + 1] + row_count;
                }
            }
        }

        float CalculateBoxScore(const postprocess::ContourRange &contour) const {
            int x_start = std::max(0, static_cast<int>(contour.min_x));
            int x_end = std::min(kDetInputSize - 1, static_cast<int>(contour.max_x));
            int y_start = std::max(0, static_cast<int>(contour.min_y));
            int y_end = std::min(kDetInputSize - 1, static_cast<int>(contour.max_y));

            if (x_start > x_end || y_start > y_end) {
                return 0.0f;
            }

            constexpr int kIntegralStride = kDetInputSize + 1;
            const int x0 = x_start;
            const int y0 = y_start;
            const int x1 = x_end + 1;
            const int y1 = y_end + 1;

            const size_t top_left = static_cast<size_t>(y0) * kIntegralStride + x0;
            const size_t top_right = static_cast<size_t>(y0) * kIntegralStride + x1;
            const size_t bottom_left = static_cast<size_t>(y1) * kIntegralStride + x0;
            const size_t bottom_right = static_cast<size_t>(y1) * kIntegralStride + x1;

            const float score_sum =
                    box_score_sum_integral_[bottom_right] - box_score_sum_integral_[bottom_left] -
                    box_score_sum_integral_[top_right] + box_score_sum_integral_[top_left];
            const uint32_t count =
                    box_score_count_integral_[bottom_right] - box_score_count_integral_[bottom_left] -
                    box_score_count_integral_[top_right] + box_score_count_integral_[top_left];

            return (count > 0) ? (score_sum / static_cast<float>(count)) : 0.0f;
        }
    };

    TextDetector::~TextDetector() = default;

    std::unique_ptr<TextDetector> TextDetector::Create(
            const std::string &model_path,
            AcceleratorType accelerator_type) {
        auto detector = std::unique_ptr<TextDetector>(new TextDetector());
        detector->impl_ = std::make_unique<Impl>();

        if (!detector->impl_->Initialize(model_path, accelerator_type)) {
            LOGE(TAG, "Failed to initialize TextDetector");
            return nullptr;
        }

        return detector;
    }

    const std::vector<RotatedRect> &TextDetector::DetectView(const uint8_t *image_data,
                                                             int width, int height, int stride,
                                                             float *detection_time_ms) {
        if (!impl_) {
            LOGE(TAG, "TextDetector not initialized");
            if (detection_time_ms) *detection_time_ms = 0.0f;
            static const std::vector<RotatedRect> kEmpty;
            return kEmpty;
        }

        return impl_->DetectView(image_data, width, height, stride, detection_time_ms);
    }

    std::vector<RotatedRect> TextDetector::Detect(const uint8_t *image_data,
                                                  int width, int height, int stride,
                                                  float *detection_time_ms) {
        return DetectView(image_data, width, height, stride, detection_time_ms);
    }

}  // namespace ppocrv5
