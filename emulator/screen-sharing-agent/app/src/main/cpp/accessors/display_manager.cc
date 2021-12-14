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

#include "display_manager.h"

#include "jvm.h"
#include "log.h"
#include "service_manager.h"

namespace screensharing {

DisplayManager::DisplayManager(Jni jni)
    : display_manager_(ServiceManager::GetServiceAsInterface(jni, "display", "android/hardware/display/IDisplayManager")) {
  JClass display_manager_class = display_manager_.GetClass();
  get_display_info_method_ = display_manager_class.GetMethodId("getDisplayInfo", "(I)Landroid/view/DisplayInfo;");

  JClass display_info_class = jni.GetClass("android/view/DisplayInfo");
  logical_width_field_ = display_info_class.GetFieldId("logicalWidth", "I");
  logical_height_field_ = display_info_class.GetFieldId("logicalHeight", "I");
  rotation_field_ = display_info_class.GetFieldId("rotation", "I");
  layer_stack_field_ = display_info_class.GetFieldId("layerStack", "I");
  flags_field_ = display_info_class.GetFieldId("flags", "I");
  display_manager_.MakeGlobal();
}

DisplayManager& DisplayManager::GetInstance(Jni jni) {
  if (instance_ == nullptr) {
    instance_ = new DisplayManager(jni);
  }
  return *instance_;
}

DisplayInfo DisplayManager::GetDisplayInfo(Jni jni, int32_t display_id) {
  DisplayManager& instance = GetInstance(jni);
  JObject display_info = instance.display_manager_.CallObjectMethod(jni, instance.get_display_info_method_, display_id);
  if (display_info.IsNull()) {
    Log::Fatal("Unable to obtain a android.view.DisplayInfo object");
  }
  int logical_width = display_info.GetIntField(jni, instance.logical_width_field_);
  int logical_height = display_info.GetIntField(instance.logical_height_field_);
  int rotation = display_info.GetIntField(instance.rotation_field_);
  int layer_stack = display_info.GetIntField(instance.layer_stack_field_);
  int flags = display_info.GetIntField(instance.flags_field_);
  return DisplayInfo(logical_width, logical_height, rotation, layer_stack, flags);
}

DisplayManager* DisplayManager::instance_ = nullptr;

}  // namespace screensharing
