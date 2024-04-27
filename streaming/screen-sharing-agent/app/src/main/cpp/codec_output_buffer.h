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

#pragma once

#include <media/NdkMediaCodec.h>

#include <string>

#include "common.h"

namespace screensharing {

class CodecOutputBuffer {
public:
  CodecOutputBuffer(AMediaCodec* codec, std::string&& log_prefix);
  ~CodecOutputBuffer();

  [[nodiscard]] bool Deque(int64_t timeout_us);

  [[nodiscard]] bool IsEndOfStream() const { return (info_.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0; }
  [[nodiscard]] bool IsConfig() const { return (info_.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) != 0; }
  uint8_t* buffer() const { return buffer_; }
  int32_t offset() const { return info_.offset; }
  int32_t size() const { return info_.size; }
  int64_t presentation_time_us() const { return info_.presentationTimeUs; }
  uint32_t flags() const { return info_.flags; }

private:
  AMediaCodec* codec_;
  std::string log_prefix_;
  uint8_t* buffer_ = nullptr;
  AMediaCodecBufferInfo info_;
  ssize_t index_ = -1;

  DISALLOW_COPY_AND_ASSIGN(CodecOutputBuffer);
};

}  // namespace screensharing
