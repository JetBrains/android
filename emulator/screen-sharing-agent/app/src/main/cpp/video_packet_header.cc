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

#include <unistd.h>
#include <sys/socket.h>

#include "string_printf.h"

namespace screensharing {

std::string VideoPacketHeader::ToDebugString() const {
  return StringPrintf("display_width:%d display_height:%d orientation:%d packet_size:%d"
                      " frame_number:%" PRId64 " origination_timestamp_us:%" PRId64 " presentation_timestamp_us:%" PRId64,
                      display_width, display_height, display_orientation, packet_size,
                      frame_number, origination_timestamp_us, presentation_timestamp_us);
}

}  // namespace screensharing
