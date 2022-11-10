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

#include "motion_event.h"

#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

MotionEvent::MotionEvent(Jni jni)
    : jni_(jni) {
}

JObject MotionEvent::ToJava() const {
  InitializeStatics(jni_);
  return android_get_device_api_level() >= 29 ?
      motion_event_class_.CallStaticObjectMethod(
          jni_, obtain_method_, down_time_millis, event_time_millis, action, pointer_count, pointer_properties, pointer_coordinates,
          meta_state, button_state, x_precision, y_precision, device_id, edge_flags, source, display_id, flags) :
      motion_event_class_.CallStaticObjectMethod(
          jni_, obtain_method_, down_time_millis, event_time_millis, action, pointer_count, pointer_properties, pointer_coordinates,
          meta_state, button_state, x_precision, y_precision, device_id, edge_flags, source, flags);
}

void MotionEvent::InitializeStatics(Jni jni) {
  if (!statics_initialized_) {
    statics_initialized_ = true;
    motion_event_class_ = jni.GetClass("android/view/MotionEvent");
    const char* signature = android_get_device_api_level() >= 29 ?
        "(JJII[Landroid/view/MotionEvent$PointerProperties;[Landroid/view/MotionEvent$PointerCoords;IIFFIIIII)Landroid/view/MotionEvent;" :
        "(JJII[Landroid/view/MotionEvent$PointerProperties;[Landroid/view/MotionEvent$PointerCoords;IIFFIIII)Landroid/view/MotionEvent;";
    obtain_method_ = motion_event_class_.GetStaticMethodId("obtain", signature);
    motion_event_class_.MakeGlobal();
  }
}

bool MotionEvent::statics_initialized_ = false;
JClass MotionEvent::motion_event_class_;
jmethodID MotionEvent::obtain_method_ = nullptr;

}  // namespace screensharing