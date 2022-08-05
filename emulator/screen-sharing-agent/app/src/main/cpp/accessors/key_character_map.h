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

// Provides access to the android/view/KeyCharacterMap.getEvents method.
class KeyCharacterMap {
public:
  static constexpr int VIRTUAL_KEYBOARD = -1;  // From android.view.KeyCharacterMap.VIRTUAL_KEYBOARD

  KeyCharacterMap(Jni jni);
  ~KeyCharacterMap();

  JObjectArray GetEvents(const uint16_t* chars, int num_chars);

private:
  void Initialize();

  Jni jni_;
  JObject java_object_;
  bool initialized_ = false;
  jmethodID get_events_method_ = nullptr;

  DISALLOW_COPY_AND_ASSIGN(KeyCharacterMap);
};

}  // namespace screensharing