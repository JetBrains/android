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

#include "jvm.h"

namespace screensharing {

// Encapsulates android.media.AudioRecord.
class AudioRecord {
public:
  AudioRecord(Jni jni, int32_t audio_sample_rate);
  AudioRecord() noexcept = default;
  ~AudioRecord();

  AudioRecord& operator=(AudioRecord&& other) noexcept;

  // Destroys the AudioRecord.
  void Release();

  void Start();
  void Stop();
  // Returns the number of audio samples read or a negative error code.
  int32_t Read(JShortArray* buf, int32_t num_samples);
  // Returns nanosecond timestamp of the audio record, if available, or a negative error code otherwise.
  int64_t GetTimestamp();
  bool IsValid() const { return !audio_record_.IsNull(); }

private:
  JObject audio_record_;
  jmethodID release_method_;
  jmethodID start_recording_method_;
  jmethodID stop_method_;
  jmethodID read_method_;
  jmethodID get_timestamp_method_;

  JObject audio_timestamp_;
  jfieldID audio_timestamp_nano_time_field_;

  DISALLOW_COPY_AND_ASSIGN(AudioRecord);
};

}  // namespace screensharing
