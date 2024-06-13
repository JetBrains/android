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

#include "jvm.h"

namespace screensharing {

// Provides access to the android.media.AudioManager methods.
class AudioManager {
public:
  // Returns the id of the audio device of the given type, or -1 if not found.
  static int32_t GetInputAudioDeviceId(Jni jni, int32_t device_type);

  AudioManager() = delete;
};

}  // namespace screensharing
