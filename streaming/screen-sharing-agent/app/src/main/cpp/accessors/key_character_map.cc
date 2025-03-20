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

#include "key_character_map.h"

#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

KeyCharacterMap::KeyCharacterMap(Jni jni)
    : jni_(jni),
      java_object_() {
}

KeyCharacterMap::~KeyCharacterMap() = default;

void KeyCharacterMap::Initialize() {
  if (!initialized_) {
    initialized_ = true;
    JClass key_character_map_class = jni_.GetClass("android/view/KeyCharacterMap");
    jmethodID load_method = key_character_map_class.GetStaticMethod("load", "(I)Landroid/view/KeyCharacterMap;");
    get_events_method_ = key_character_map_class.GetMethod("getEvents", "([C)[Landroid/view/KeyEvent;");
    java_object_ = key_character_map_class.CallStaticObjectMethod(load_method, VIRTUAL_KEYBOARD);
    if (java_object_.IsNull()) {
      Log::Fatal(KEY_CHARACTER_MAP_ERROR, jni_.GetAndClearException(), "Unable to load a android.view.KeyCharacterMap");
    }
    java_object_.MakeGlobal();
  }
}

JObjectArray KeyCharacterMap::GetEvents(const uint16_t* chars, int num_chars) {
  Initialize();
  return JObjectArray(java_object_.CallObjectMethod(jni_, get_events_method_, JCharArray::Create(jni_, num_chars, chars).ref()));
}

}  // namespace screensharing