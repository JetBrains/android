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

#include <aaudio/AAudio.h>
#include <media/NdkMediaCodec.h>

#include <atomic>
#include <cstddef>
#include <mutex>
#include <thread>

#include "common.h"
#include "geom.h"
#include "jvm.h"
#include "socket_writer.h"

namespace screensharing {

// Processes control socket commands.
class AudioStreamer {
public:
  explicit AudioStreamer(int socket_fd);
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
  aaudio_data_callback_result_t ConsumeAudioData(AAudioStream* stream, uint8_t* audio_data, int32_t num_frames);

  void DeleteAudioStreamAndBuilder();
  static aaudio_data_callback_result_t AudioDataCallback(AAudioStream* stream, void* user_data, void* audio_data, int32_t num_frames);

  std::thread thread_;
  SocketWriter writer_;
  std::atomic_bool streamer_stopped_ = true;
  AAudioStreamBuilder* stream_builder_ = nullptr;
  AAudioStream* stream_ = nullptr;
  int32_t consequent_queue_error_count_ = 0;
  int32_t consequent_deque_error_count_ = 0;
  int64_t last_presentation_timestamp_us_ = 0;
  int32_t num_frames_in_last_sample_ = 0;

  std::recursive_mutex mutex_;
  AMediaCodec* codec_ = nullptr;  // GUARDED_BY(mutex_)
  AMediaCodec* running_codec_ = nullptr;  // GUARDED_BY(mutex_)
  bool codec_stop_pending_ = false;  // GUARDED_BY(mutex_)
  AMediaFormat* media_format_ = nullptr;  // GUARDED_BY(mutex_)

  DISALLOW_COPY_AND_ASSIGN(AudioStreamer);
};

}  // namespace screensharing
