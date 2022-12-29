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

#include <vector>

#include "accessors/display_listener_dispatcher.h"
#include "jvm.h"
#include "log.h"
#include "service_manager.h"

namespace screensharing {

using namespace std;

DisplayManager::DisplayManager(Jni jni)
    : display_listeners_(new vector<DisplayListener*>()) {
  display_manager_class_ = jni.GetClass("android/hardware/display/DisplayManagerGlobal");
  jmethodID  get_instance_method =
      display_manager_class_.GetStaticMethodId("getInstance", "()Landroid/hardware/display/DisplayManagerGlobal;");
  display_manager_ = display_manager_class_.CallStaticObjectMethod(get_instance_method);

  get_display_info_method_ = display_manager_class_.GetMethodId("getDisplayInfo", "(I)Landroid/view/DisplayInfo;");

  JClass display_info_class = jni.GetClass("android/view/DisplayInfo");
  logical_width_field_ = display_info_class.GetFieldId("logicalWidth", "I");
  logical_height_field_ = display_info_class.GetFieldId("logicalHeight", "I");
  rotation_field_ = display_info_class.GetFieldId("rotation", "I");
  layer_stack_field_ = display_info_class.GetFieldId("layerStack", "I");
  flags_field_ = display_info_class.GetFieldId("flags", "I");

  display_manager_class_.MakeGlobal();
  display_manager_.MakeGlobal();

  if (android_get_device_api_level() >= 29) {
    display_listener_dispatcher_ = new DisplayListenerDispatcher(display_manager_class_, display_manager_);
  }
}

DisplayManager::~DisplayManager() {
  delete display_listener_dispatcher_;
  delete display_listeners_;
}

DisplayManager& DisplayManager::GetInstance(Jni jni) {
  static DisplayManager instance(jni);
  return instance;
}

DisplayInfo DisplayManager::GetDisplayInfo(Jni jni, int32_t display_id) {
  DisplayManager& instance = GetInstance(jni);
  JObject display_info = instance.display_manager_.CallObjectMethod(jni, instance.get_display_info_method_, display_id);
  if (display_info.IsNull()) {
    Log::Fatal("Unable to obtain a android.view.DisplayInfo object");
  }
  Log::D("display_info=%s", display_info.ToString().c_str());
  int logical_width = display_info.GetIntField(jni, instance.logical_width_field_);
  int logical_height = display_info.GetIntField(instance.logical_height_field_);
  int rotation = display_info.GetIntField(instance.rotation_field_);
  int layer_stack = display_info.GetIntField(instance.layer_stack_field_);
  int flags = display_info.GetIntField(instance.flags_field_);
  return DisplayInfo(logical_width, logical_height, rotation, layer_stack, flags);
}

void DisplayManager::RegisterDisplayListener(Jni jni, DisplayManager::DisplayListener* listener) {
  DisplayManager& instance = GetInstance(jni);
  if (instance.display_listener_dispatcher_ == nullptr) {
    return;
  }
  for (;;) {
    auto old_listeners = instance.display_listeners_.load();
    auto new_listeners = new vector<DisplayListener*>(*old_listeners);
    new_listeners->push_back(listener);
    if (instance.display_listeners_.compare_exchange_strong(old_listeners, new_listeners)) {
      if (old_listeners->empty()) {
        instance.display_listener_dispatcher_->Start();
      }
      delete old_listeners;
      break;
    }
    delete new_listeners;
  }
}

void DisplayManager::UnregisterDisplayListener(Jni jni, DisplayManager::DisplayListener* listener) {
  DisplayManager& instance = GetInstance(jni);
  if (instance.display_listener_dispatcher_ == nullptr) {
    return;
  }
  for (;;) {
    auto old_listeners = instance.display_listeners_.load();
    auto new_listeners = new vector<DisplayListener*>(*old_listeners);
    auto pos = find(new_listeners->begin(), new_listeners->end(), listener);
    if (pos != new_listeners->end()) {
      new_listeners->erase(pos);
    }
    if (new_listeners->empty()) {
      instance.display_listener_dispatcher_->Stop();
    }
    if (instance.display_listeners_.compare_exchange_strong(old_listeners, new_listeners)) {
      delete old_listeners;
      break;
    }
    delete new_listeners;
  }
}

void DisplayManager::OnDisplayAdded(Jni jni, int32_t display_id) {
  DisplayManager& instance = GetInstance(jni);
  for (auto listener : *instance.display_listeners_.load()) {
    listener->OnDisplayAdded(display_id);
  }
}

void DisplayManager::OnDisplayRemoved(Jni jni, int32_t display_id) {
  DisplayManager& instance = GetInstance(jni);
  for (auto listener : *instance.display_listeners_.load()) {
    listener->OnDisplayRemoved(display_id);
  }
}

void DisplayManager::OnDisplayChanged(Jni jni, int32_t display_id) {
  DisplayManager& instance = GetInstance(jni);
  for (auto listener : *instance.display_listeners_.load()) {
    listener->OnDisplayChanged(display_id);
  }
}

}  // namespace screensharing
