/*
 * Copyright (C) 2025 The Android Open Source Project
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

#include "audio_reader.h"
#include "jvm.h"
#include "codec_handle.h"

namespace screensharing {

// Pumps data from REMOTE_SUBMIX audio device to AMediaCodec. Used for old Android versions.
class RemoteSubmixReader : public AudioReader {
public:
  RemoteSubmixReader(int32_t num_channels, int32_t sample_rate);
  virtual ~RemoteSubmixReader();

  virtual void Start(CodecHandle* codec_handle);
  virtual void Stop();

protected:
  bool StartAudioStream();
  aaudio_data_callback_result_t ConsumeAudioData(AAudioStream* stream, uint8_t* audio_data, int32_t num_frames);

  void DeleteAudioStreamAndBuilder();
  static aaudio_data_callback_result_t AudioDataCallback(AAudioStream* stream, void* user_data, void* audio_data, int32_t num_frames);

  AAudioStreamBuilder* stream_builder_ = nullptr;
  AAudioStream* stream_ = nullptr;
};

}  // namespace screensharing
