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

#include <android/input.h>

#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

KeyEvent::KeyEvent(Jni jni)
    : source(AINPUT_SOURCE_KEYBOARD),
      jni_(jni) {
}

JObject KeyEvent::ToJava() const {
  InitializeConstructor(jni_);
  return key_event_class_.NewObject(
      jni_, constructor_, down_time_millis, event_time_millis, action, code, repeat, meta_state, device_id, scancode, flags, source);
}

int32_t KeyEvent::GetKeyCode(const JObject& key_event) {
  InitializeFieldIds(key_event);
  return key_event.GetIntField(key_code_field_);
}

int32_t KeyEvent::GetAction(const JObject& key_event) {
  InitializeFieldIds(key_event);
  return key_event.GetIntField(action_field_);
}

void KeyEvent::InitializeConstructor(Jni jni) {
  if (constructor_ == nullptr) {
    key_event_class_ = jni.GetClass("android/view/KeyEvent");
    constructor_ = key_event_class_.GetConstructor("(JJIIIIIIII)V");
    key_event_class_.MakeGlobal();
  }
}

void KeyEvent::InitializeFieldIds(const JObject& key_event) {
  JClass clazz = key_event.GetClass();
  key_code_field_ = clazz.GetFieldId("mKeyCode", "I");
  action_field_ = clazz.GetFieldId("mAction", "I");
}

JClass KeyEvent::key_event_class_;
jmethodID KeyEvent::constructor_ = nullptr;
jfieldID KeyEvent::key_code_field_ = nullptr;
jfieldID KeyEvent::action_field_ = nullptr;

}  // namespace screensharing