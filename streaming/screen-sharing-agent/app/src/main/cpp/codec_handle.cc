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

#include "codec_handle.h"

#include "log.h"

namespace screensharing {

using namespace std;

CodecHandle::CodecHandle(MediaCodec&& codec, string&& log_prefix)
    : log_prefix_(log_prefix),
      codec_(std::move(codec)) {
}

CodecHandle::~CodecHandle() {
  Stop();
}

bool CodecHandle::Start() {
  unique_lock lock(mutex_);
  if (stopped_) {
    return false;
  }
  media_status_t status = AMediaCodec_start(codec_);
  if (status != AMEDIA_OK) {
    Log::W("%serror starting codec: %d", log_prefix_.c_str(), status);
    return false;
  }
  running_ = true;
  return true;
}

void CodecHandle::Stop() {
  unique_lock lock(mutex_);
  stopped_ = true;
  if (running_) {
    Log::D("%sstopping codec", log_prefix_.c_str());
    AMediaCodec_stop(codec_);
    running_ = false;
  }
}

bool CodecHandle::IsStopped() {
  unique_lock lock(mutex_);
  return stopped_;
}

}  // namespace screensharing
