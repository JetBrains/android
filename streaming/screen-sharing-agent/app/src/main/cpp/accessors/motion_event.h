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

#include <cstdint>
#include <string>

#include <common.h>
#include "geom.h"
#include "jvm.h"

namespace screensharing {

// Mechanism for creation of android.view.MotionEvent objects.
class MotionEvent {
public:
  MotionEvent(Jni jni);

  // Returns a android.view.MotionEvent Java object.
  JObject ToJava() const;

  jlong down_time_millis = 0;
  jlong event_time_millis = 0;
  jint action = 0;
  jint pointer_count = 0;
  jobjectArray pointer_properties = nullptr;   // MotionEvent.PointerProperties[]
  jobjectArray pointer_coordinates = nullptr;  // MotionEvent.PointerCoords[]
  jint meta_state = 0;
  jint button_state = 0;
  jfloat x_precision = 1;
  jfloat y_precision = 1;
  jint device_id = 0;
  jint edge_flags = 0;
  jint source = AINPUT_SOURCE_STYLUS | AINPUT_SOURCE_TOUCHSCREEN;
  jint display_id = 0;
  jint flags = 0;

private:
  static void InitializeStatics(Jni jni);

  static bool statics_initialized_;
  static JClass motion_event_class_;
  static jmethodID obtain_method_;

  Jni jni_;

  DISALLOW_COPY_AND_ASSIGN(MotionEvent);
};

}  // namespace screensharing