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

#include "video_packet_header.h"

#include <inttypes.h>

#include "string_printf.h"

namespace screensharing {

std::string VideoPacketHeader::ToDebugString() const {
  return StringPrintf("display_id:%d display_width:%d display_height:%d orientation:%d orientation_correction:%d flags:%d bit_rate=%d"
                      " frame_number:%u origination_timestamp_us:%" PRId64 " presentation_timestamp_us:%" PRId64 " packet_size:%d",
                      display_id, display_width, display_height, display_orientation, display_orientation_correction, flags,
                      bit_rate, frame_number, origination_timestamp_us, presentation_timestamp_us, packet_size);
}

size_t VideoPacketHeader::SIZE = offsetof(VideoPacketHeader, packet_size) + sizeof(packet_size);

}  // namespace screensharing
