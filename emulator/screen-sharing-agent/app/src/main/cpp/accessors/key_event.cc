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

#include "key_event.h"

#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

KeyEvent::KeyEvent(Jni jni)
    : jni_(jni) {
}

JObject KeyEvent::ToJava() const {
  InitializeStatics(jni_);
  return key_event_class_.NewObject(
      jni_, constructor_, down_time_millis, event_time_millis, action, code, repeat, meta_state, device_id, scancode, flags, source);
}

void KeyEvent::InitializeStatics(Jni jni) {
  if (!statics_initialized_) {
    statics_initialized_ = true;
    key_event_class_ = jni.GetClass("android/view/KeyEvent");
    constructor_ = key_event_class_.GetConstructorId("(JJIIIIIIII)V");
    key_event_class_.MakeGlobal();
  }
}

bool KeyEvent::statics_initialized_ = false;
JClass KeyEvent::key_event_class_;
jmethodID KeyEvent::constructor_ = nullptr;

}  // namespace screensharing