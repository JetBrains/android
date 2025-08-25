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

#include <mutex>

#include "common.h"

namespace screensharing {

// Streams audio to a socket.
class CodecHandle {
public:
  // Takes ownership of the codec.
  CodecHandle(AMediaCodec* codec, std::string&& log_prefix);
  ~CodecHandle();

  // Starts the codec unless there is a pending stop request. Returns true if the codec was started.
  [[nodiscard]] bool Start();
  // Stops the codec if is is running. Otherwise creates a pending stop request.
  void Stop();

  AMediaCodec* codec() const { return codec_; }

private:
  std::string log_prefix_;
  std::recursive_mutex mutex_;
  AMediaCodec* codec_ = nullptr;  // GUARDED_BY(mutex_)
  bool running_ = false;  // GUARDED_BY(mutex_)
  bool stop_pending_ = false;  // GUARDED_BY(mutex_)

  DISALLOW_COPY_AND_ASSIGN(CodecHandle);
};

}  // namespace screensharing
