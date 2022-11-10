/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <inttypes.h>

#include <cstdint>
#include <string>

namespace screensharing {

// The header of a video packet.
struct VideoPacketHeader {
  int32_t display_width;
  int32_t display_height;
  int16_t display_orientation; // In quadrants.
  // The difference between display_orientation and the orientation according to the DisplayInfo Android data structure.
  int16_t display_orientation_correction; // In quadrants.
  int32_t packet_size;
  int64_t frame_number;  // Starts from 1.
  int64_t origination_timestamp_us;
  int64_t presentation_timestamp_us;  // Zero means a config packet.

  std::string ToDebugString() const;
};

}  // namespace screensharing
