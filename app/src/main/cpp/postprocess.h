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

#ifndef PPOCRV5_POSTPROCESS_H
#define PPOCRV5_POSTPROCESS_H

#include <cstddef>
#include <cstdint>
#include <vector>

#include "text_detector.h"

namespace ppocrv5::postprocess {

    struct Point {
        float x = 0.0f;
        float y = 0.0f;
    };

    struct ContourRange {
        int offset = 0;
        int size = 0;
        float min_x = 0.0f;
        float max_x = 0.0f;
        float min_y = 0.0f;
        float max_y = 0.0f;
    };

    struct ContourScratch {
        std::vector<uint8_t> ds_map;
        std::vector<uint32_t> visit_marks;
        uint32_t visit_token = 1;
        std::vector<int> queue;
        std::vector<Point> contour_points;
        std::vector<ContourRange> contours;
        std::vector<Point> work_points;
        std::vector<Point> hull_points;
    };

    inline const Point *ContourData(const ContourScratch &scratch, const ContourRange &contour) {
        return scratch.contour_points.data() + contour.offset;
    }

    void FindContours(const uint8_t *binary_map,
                      int width, int height,
                      ContourScratch *scratch);

    RotatedRect MinAreaRect(const ContourRange &contour,
                            ContourScratch *scratch);

    void FilterAndSortBoxes(
            const std::vector<RotatedRect> &boxes,
            float min_confidence, float min_area,
            std::vector<RotatedRect> *out);

} // namespace ppocrv5::postprocess

#endif  // PPOCRV5_POSTPROCESS_H
