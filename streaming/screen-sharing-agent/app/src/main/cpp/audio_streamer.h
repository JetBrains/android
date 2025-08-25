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

#include <atomic>
#include <thread>

#include "accessors/audio_record.h"
#include "audio_reader.h"
#include "common.h"
#include "jvm.h"
#include "codec_handle.h"
#include "socket_writer.h"

namespace screensharing {

// Streams audio to a socket.
class AudioStreamer {
public:
  explicit AudioStreamer(SocketWriter* writer);
  ~AudioStreamer();

  // Starts the streamer's thread.
  void Start();
  // Stops the streamer. Waits for the streamer's thread to terminate.
  void Stop();

private:
  void Run();
  // Returns true if audio capture started successfully.
  bool StartAudioCapture();
  void StopAudioCapture();
  void StopCodec();

  std::thread thread_;
  SocketWriter* writer_;
  std::atomic_bool streamer_stopped_ = true;
  AudioReader* audio_reader_ = nullptr;
  int32_t consequent_deque_error_count_ = 0;

  AMediaFormat* media_format_ = nullptr;
  CodecHandle* codec_handle_ = nullptr;

  DISALLOW_COPY_AND_ASSIGN(AudioStreamer);
};

}  // namespace screensharing
