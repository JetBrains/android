/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <android/input.h>

#include "jvm.h"

namespace screensharing {

// Copied from android/os/InputEventInjectionSync.h.
enum class InputEventInjectionSync : int32_t {
  NONE = 0,
  WAIT_FOR_RESULT = 1,
  WAIT_FOR_FINISHED = 2,
};

// Provides access to the android.hardware.input.IInputManager.injectInputEvent method.
class InputManager {
public:
  static void InjectInputEvent(Jni jni, const JObject& input_event, InputEventInjectionSync mode);

private:
  InputManager(Jni jni);
  ~InputManager();
  static InputManager& GetInstance(Jni jni);

  JObject input_manager_;
  jmethodID inject_input_event_method_;
};

}  // namespace screensharing
