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

#include "common.h"
#include "jvm.h"

namespace screensharing {

// Creates and manipulates MotionEvent.PointerProperties and MotionEvent.PointerCoords objects.
class PointerHelper {
public:
  PointerHelper(Jni jni);
  ~PointerHelper();

  JObjectArray NewPointerPropertiesArray(int32_t length);
  JObject NewPointerProperties();
  void SetPointerToolType(const JObject& pointer_properties, int32_t tool_type);
  void SetPointerId(const JObject& pointer_properties, int32_t id);

  JObjectArray NewPointerCoordsArray(int32_t length);
  JObject NewPointerCoords();
  void SetPointerCoords(const JObject& pointer_coords, float x, float y);
  void SetPointerPressure(const JObject& pointer_coords, float pressure);
  void SetAxisValue(const JObject& pointer_coords, int32_t axis, float value);
  void ClearPointerCoords(const JObject& pointer_coords);

private:
  Jni jni_;
  // MotionEvent.PointerProperties
  JClass pointer_properties_class_;
  jmethodID pointer_properties_ctor_;
  jfieldID id_field_;
  jfieldID tool_type_field_;
  // MotionEvent.PointerCoords
  JClass pointer_coords_class_;
  jmethodID pointer_coords_ctor_;
  jmethodID pointer_coords_set_axis_value_method_;
  jmethodID pointer_coords_clear_method_;
  jfieldID x_field_;
  jfieldID y_field_;
  jfieldID pressure_field_;
  jfieldID size_field_;
  jfieldID touch_major_field_;
  jfieldID touch_minor_field_;
  jfieldID tool_major_field_;
  jfieldID tool_minor_field_;
  jfieldID orientation_field_;

  DISALLOW_COPY_AND_ASSIGN(PointerHelper);
};

}  // namespace screensharing
