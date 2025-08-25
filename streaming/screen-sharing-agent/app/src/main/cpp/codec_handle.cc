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

CodecHandle::CodecHandle(AMediaCodec* codec, string&& log_prefix)
    : log_prefix_(log_prefix),
      codec_(codec) {
}

CodecHandle::~CodecHandle() {
  Stop();
  AMediaCodec_delete(codec_);
}

bool CodecHandle::Start() {
  unique_lock lock(mutex_);
  if (stop_pending_) {
    return false;
  }
  media_status_t res = AMediaCodec_start(codec_);
  if (res != AMEDIA_OK) {
    Log::W("%serror starting codec: %d", log_prefix_.c_str(), res);
    return false;
  }
  running_ = true;
  return true;
}

void CodecHandle::Stop() {
  unique_lock lock(mutex_);
  if (running_) {
    Log::D("%sstopping codec", log_prefix_.c_str());
    AMediaCodec_stop(codec_);
    running_ = false;
  } else {
    stop_pending_ = true;
  }
}

}  // namespace screensharing
