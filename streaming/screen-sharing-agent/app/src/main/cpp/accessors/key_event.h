/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <cstdint>
#include <string>

#include <common.h>
#include "geom.h"
#include "jvm.h"

namespace screensharing {

// Mechanism for creation of android.view.KeyEvent objects.
class KeyEvent {
public:
  KeyEvent(Jni jni);

  // Returns a android.view.KeyEvent Java object.
  JObject ToJava() const;

  jlong down_time_millis = 0;
  jlong event_time_millis = 0;
  jint action = 0;
  jint code = 0;
  jint repeat = 0;
  jint meta_state = 0;
  jint device_id = 0;
  jint scancode = 0;
  jint flags = 0;
  jint source = AINPUT_SOURCE_KEYBOARD;

private:
  static void InitializeStatics(Jni jni);

  static bool statics_initialized_;
  static JClass key_event_class_;
  static jmethodID constructor_;

  Jni jni_;

  DISALLOW_COPY_AND_ASSIGN(KeyEvent);
};

}  // namespace screensharing