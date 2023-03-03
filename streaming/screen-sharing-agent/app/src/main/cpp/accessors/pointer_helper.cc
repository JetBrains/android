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

#include "pointer_helper.h"

#include <unistd.h>
#include <android/input.h>

#include "log.h"

namespace screensharing {

using namespace std;

PointerHelper::PointerHelper(Jni jni)
    : jni_(jni),
      pointer_properties_class_(),
      pointer_coords_class_() {
  pointer_properties_class_ = jni_.GetClass("android/view/MotionEvent$PointerProperties");
  pointer_properties_ctor_ = pointer_properties_class_.GetConstructor("()V");
  id_field_ = pointer_properties_class_.GetFieldId("id", "I");
  tool_type_field_ = pointer_properties_class_.GetFieldId("toolType", "I");
  pointer_properties_class_.MakeGlobal();

  pointer_coords_class_ = jni_.GetClass("android/view/MotionEvent$PointerCoords");
  pointer_coords_ctor_ = pointer_coords_class_.GetConstructor("()V");
  pointer_coords_set_axis_value_method_ = pointer_coords_class_.GetMethod("setAxisValue", "(IF)V");
  pointer_coords_clear_method_ = pointer_coords_class_.GetMethod("clear", "()V");
  x_field_ = pointer_coords_class_.GetFieldId("x", "F");
  y_field_ = pointer_coords_class_.GetFieldId("y", "F");
  pressure_field_ = pointer_coords_class_.GetFieldId("pressure", "F");
  size_field_ = pointer_coords_class_.GetFieldId("size", "F");
  touch_major_field_ = pointer_coords_class_.GetFieldId("touchMajor", "F");
  touch_minor_field_ = pointer_coords_class_.GetFieldId("touchMinor", "F");
  tool_major_field_ = pointer_coords_class_.GetFieldId("toolMajor", "F");
  tool_minor_field_ = pointer_coords_class_.GetFieldId("toolMinor", "F");
  orientation_field_ = pointer_coords_class_.GetFieldId("orientation", "F");
  pointer_coords_class_.MakeGlobal();
}

PointerHelper::~PointerHelper() = default;

JObjectArray PointerHelper::NewPointerPropertiesArray(int32_t length) {
  return pointer_properties_class_.NewObjectArray(jni_, length, nullptr);
}

JObject PointerHelper::NewPointerProperties() {
  JObject pointer_properties = pointer_properties_class_.NewObject(jni_, pointer_properties_ctor_);
  SetPointerToolType(pointer_properties, AMOTION_EVENT_TOOL_TYPE_FINGER);
  return pointer_properties;
}

void PointerHelper::SetPointerToolType(const JObject& pointer_properties, int32_t tool_type) {
  pointer_properties.SetIntField(tool_type_field_, tool_type);
}

void PointerHelper::SetPointerId(const JObject& pointer_properties, int32_t id) {
  pointer_properties.SetIntField(id_field_, id);
}

JObjectArray PointerHelper::NewPointerCoordsArray(int32_t length) {
  return pointer_coords_class_.NewObjectArray(jni_, length, nullptr);
}

JObject PointerHelper::NewPointerCoords() {
  JObject object = pointer_coords_class_.NewObject(jni_, pointer_coords_ctor_);
  object.SetFloatField(size_field_, 1);
  return object;
}

void PointerHelper::SetPointerCoords(const JObject& pointer_coords, float x, float y) {
  pointer_coords.SetFloatField(x_field_, x);
  pointer_coords.SetFloatField(y_field_, y);
}

void PointerHelper::SetPointerPressure(const JObject& pointer_coords, float pressure) {
  pointer_coords.SetFloatField(pressure_field_, pressure);
}

void PointerHelper::SetAxisValue(const JObject& pointer_coords, int32_t axis, float value) {
  pointer_coords.CallVoidMethod(jni_, pointer_coords_set_axis_value_method_, axis, value);
}

void PointerHelper::ClearPointerCoords(const JObject& pointer_coords) {
  pointer_coords.CallVoidMethod(jni_, pointer_coords_clear_method_);
}

}  // namespace screensharing
