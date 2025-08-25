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

#include <thread>

#include "accessors/audio_record.h"
#include "audio_reader.h"
#include "codec_handle.h"

namespace screensharing {

// Pumps data from AudioRecord to AMediaCodec. Used for recent Android versions.
class AudioRecordReader : public AudioReader {
public:
  // Creates a new AudioRecordReader.
  AudioRecordReader(int32_t num_channels, int32_t sample_rate);
  virtual ~AudioRecordReader();

  // Starts the reader's thread.
  virtual void Start(CodecHandle* codec_handle);
  // Stops the reader. Waits for the reader's thread to terminate.
  virtual void Stop();

private:
  void Run();
  void ReadUntilStopped();

  std::thread thread_;
  AudioRecord audio_record_;
};

}  // namespace screensharing
