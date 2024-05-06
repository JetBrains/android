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

#include "codec_output_buffer.h"

#include <cinttypes>

#include "log.h"

namespace screensharing {

using namespace std;

CodecOutputBuffer::CodecOutputBuffer(AMediaCodec* codec, std::string&& log_prefix)
    : codec_(codec),
      log_prefix_(log_prefix) {
}

CodecOutputBuffer::~CodecOutputBuffer() {
  if (index_ >= 0) {
    AMediaCodec_releaseOutputBuffer(codec_, static_cast<size_t>(index_), false);
  }
}

bool CodecOutputBuffer::Deque(int64_t timeout_us) {
  index_ = AMediaCodec_dequeueOutputBuffer(codec_, &info_, timeout_us);
  if (index_ < 0) {
    Log::W("%sAMediaCodec_dequeueOutputBuffer returned %ld", log_prefix_.c_str(), static_cast<long>(index_));
    return false;
  }
  if (Log::IsEnabled(Log::Level::VERBOSE)) {
    Log::V("%sCodecOutputBuffer::Deque: index:%ld offset:%d size:%d flags:0x%x, presentationTimeUs:%" PRId64,
           log_prefix_.c_str(), static_cast<long>(index_), info_.offset, info_.size, info_.flags, info_.presentationTimeUs);
  }
  buffer_ = AMediaCodec_getOutputBuffer(codec_, static_cast<size_t>(index_), nullptr);
  if (buffer_ == nullptr) {
    Log::W("%sAMediaCodec_getOutputBuffer(codec, %ld, &size) returned null", log_prefix_.c_str(), static_cast<long>(index_));
    return false;
  }
  return true;
}

}  // namespace screensharing
