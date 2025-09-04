/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "codec_input_buffer.h"

#include "log.h"

namespace screensharing {

CodecInputBuffer::CodecInputBuffer(AMediaCodec* codec, std::string&& log_prefix)
    : log_prefix_(log_prefix),
      codec_(codec) {
}

bool CodecInputBuffer::Deque(int64_t timeout_us) {
  index = AMediaCodec_dequeueInputBuffer(codec_, timeout_us);
  if (index < 0) {
    Log::W("%sMediaCodec_dequeueInputBuffer returned %ld", log_prefix_.c_str(), static_cast<long>(index));
    return false;
  }
  buffer = AMediaCodec_getInputBuffer(codec_, static_cast<size_t>(index), &size);
  if (buffer == nullptr) {
    Log::W("%sAMediaCodec_getInputBuffer(codec, %ld, &size) returned null", log_prefix_.c_str(), static_cast<long>(index));
    return false;
  }
  return true;
}

bool CodecInputBuffer::Queue(size_t data_size, uint64_t presentation_time_us, uint32_t flags) {
  auto res = AMediaCodec_queueInputBuffer(codec_, index, 0, data_size, presentation_time_us, flags);
  if (res == AMEDIA_OK) {
    return true;
  }
  Log::W("%sAMediaCodec_queueInputBuffer returned %d", log_prefix_.c_str(), res);
  return false;
}

}  // namespace screensharing
