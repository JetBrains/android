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
#include "surface.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex; // Protects initialization of static fields.

void DisplayManager::InitializeStatics(Jni jni) {
  scoped_lock lock(static_initialization_mutex);

  if (display_listeners_ == nullptr) {
    display_listeners_ = new vector<DisplayListener*>();
    display_manager_global_class_ = jni.GetClass("android/hardware/display/DisplayManagerGlobal");
    jmethodID get_instance_method =
        display_manager_global_class_.GetStaticMethodId("getInstance", "()Landroid/hardware/display/DisplayManagerGlobal;");
    display_manager_global_ = display_manager_global_class_.CallStaticObjectMethod(get_instance_method);

    get_display_info_method_ = display_manager_global_class_.GetMethodId("getDisplayInfo", "(I)Landroid/view/DisplayInfo;");

    JClass display_info_class = jni.GetClass("android/view/DisplayInfo");
    logical_width_field_ = display_info_class.GetFieldId("logicalWidth", "I");
    logical_height_field_ = display_info_class.GetFieldId("logicalHeight", "I");
    logical_density_dpi_field_ = display_info_class.GetFieldId("logicalDensityDpi", "I");
    rotation_field_ = display_info_class.GetFieldId("rotation", "I");
    layer_stack_field_ = display_info_class.GetFieldId("layerStack", "I");
    flags_field_ = display_info_class.GetFieldId("flags", "I");

    int api_level = android_get_device_api_level();
    if (api_level >= 29) {
      display_listener_dispatcher_ = new DisplayListenerDispatcher();
    }

    if (api_level >= 33) {
      display_manager_class_ = jni.GetClass("android/hardware/display/DisplayManager");
      create_virtual_display_method_ = display_manager_class_.FindStaticMethod(
          "createVirtualDisplay", "(Ljava/lang/String;IIILandroid/view/Surface;)Landroid/hardware/display/VirtualDisplay;");
    }

    display_manager_global_class_.MakeGlobal();
    display_manager_global_.MakeGlobal();
    display_manager_class_.MakeGlobal();
  }
}

DisplayInfo DisplayManager::GetDisplayInfo(Jni jni, int32_t display_id) {
  InitializeStatics(jni);
  JObject display_info = display_manager_global_.CallObjectMethod(jni, get_display_info_method_, display_id);
  if (display_info.IsNull()) {
    JObject exception = jni.GetAndClearException();
    if (!exception.IsNull()) {
      Log::Fatal("Unable to obtain a android.view.DisplayInfo - %s", exception.ToString().c_str());
    }
    Log::Fatal("Unable to obtain a android.view.DisplayInfo");
  }
  if (Log::IsEnabled(Log::Level::DEBUG)) {
    Log::D("display_info=%s", display_info.ToString().c_str());
  }
  int logical_width = display_info.GetIntField(jni, logical_width_field_);
  int logical_height = display_info.GetIntField(logical_height_field_);
  int logical_density_dpi = display_info.GetIntField(logical_density_dpi_field_);
  int rotation = display_info.GetIntField(rotation_field_);
  int layer_stack = display_info.GetIntField(layer_stack_field_);
  int flags = display_info.GetIntField(flags_field_);
  return DisplayInfo(logical_width, logical_height, logical_density_dpi, rotation, layer_stack, flags);
}

void DisplayManager::RegisterDisplayListener(Jni jni, DisplayManager::DisplayListener* listener) {
  InitializeStatics(jni);
  if (display_listener_dispatcher_ == nullptr) {
    return;
  }
  for (;;) {
    auto old_listeners = display_listeners_.load();
    auto new_listeners = new vector<DisplayListener*>(*old_listeners);
    new_listeners->push_back(listener);
    if (display_listeners_.compare_exchange_strong(old_listeners, new_listeners)) {
      if (old_listeners->empty()) {
        display_listener_dispatcher_->Start();
      }
      delete old_listeners;
      break;
    }
    delete new_listeners;
  }
}

void DisplayManager::UnregisterDisplayListener(Jni jni, DisplayManager::DisplayListener* listener) {
  InitializeStatics(jni);
  if (display_listener_dispatcher_ == nullptr) {
    return;
  }
  for (;;) {
    auto old_listeners = display_listeners_.load();
    auto new_listeners = new vector<DisplayListener*>(*old_listeners);
    auto pos = find(new_listeners->begin(), new_listeners->end(), listener);
    if (pos != new_listeners->end()) {
      new_listeners->erase(pos);
    }
    if (new_listeners->empty()) {
      display_listener_dispatcher_->Stop();
    }
    if (display_listeners_.compare_exchange_strong(old_listeners, new_listeners)) {
      delete old_listeners;
      break;
    }
    delete new_listeners;
  }
}

void DisplayManager::OnDisplayAdded(Jni jni, int32_t display_id) {
  InitializeStatics(jni);
  for (auto listener : *display_listeners_.load()) {
    listener->OnDisplayAdded(display_id);
  }
}

void DisplayManager::OnDisplayRemoved(Jni jni, int32_t display_id) {
  InitializeStatics(jni);
  for (auto listener : *display_listeners_.load()) {
    listener->OnDisplayRemoved(display_id);
  }
}

void DisplayManager::OnDisplayChanged(Jni jni, int32_t display_id) {
  InitializeStatics(jni);
  for (auto listener : *display_listeners_.load()) {
    listener->OnDisplayChanged(display_id);
  }
}

VirtualDisplay DisplayManager::CreateVirtualDisplay(
    Jni jni, const char* name, int32_t width, int32_t height, int32_t display_id, ANativeWindow* surface) {
  InitializeStatics(jni);
  if (create_virtual_display_method_ == nullptr) {
    Log::E("The DisplayManager.createVirtualDisplay static method is unavailable");
    return VirtualDisplay();
  }

  return VirtualDisplay(jni, display_manager_class_.CallStaticObjectMethod(
      jni, create_virtual_display_method_, JString(jni, name).ref(), width, height, display_id, SurfaceToJava(jni, surface).ref()));
}

JClass DisplayManager::display_manager_global_class_;
JObject DisplayManager::display_manager_global_;
jmethodID DisplayManager::get_display_info_method_ = nullptr;
jfieldID DisplayManager::logical_width_field_ = nullptr;
jfieldID DisplayManager::logical_height_field_ = nullptr;
jfieldID DisplayManager::logical_density_dpi_field_ = nullptr;
jfieldID DisplayManager::rotation_field_ = nullptr;
jfieldID DisplayManager::layer_stack_field_ = nullptr;
jfieldID DisplayManager::flags_field_ = nullptr;
JClass DisplayManager::display_manager_class_;
jmethodID DisplayManager::create_virtual_display_method_ = nullptr;

atomic<vector<DisplayManager::DisplayListener*>*> DisplayManager::display_listeners_;

DisplayListenerDispatcher* DisplayManager::display_listener_dispatcher_ = nullptr;

}  // namespace screensharing
