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
#include "agent.h"
#include "jvm.h"
#include "log.h"
#include "service_manager.h"
#include "surface.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex; // Protects initialization of static fields.

void DisplayManager::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex);

  if (display_manager_global_class_.IsNull()) {
    display_manager_global_class_ = jni.GetClass("android/hardware/display/DisplayManagerGlobal");
    jmethodID get_instance_method =
        display_manager_global_class_.GetStaticMethod("getInstance", "()Landroid/hardware/display/DisplayManagerGlobal;");
    display_manager_global_ = display_manager_global_class_.CallStaticObjectMethod(get_instance_method);

    get_display_info_method_ = display_manager_global_class_.GetMethod("getDisplayInfo", "(I)Landroid/view/DisplayInfo;");
    get_display_ids_method_ = display_manager_global_class_.GetMethod("getDisplayIds", "()[I");

    JClass display_info_class = jni.GetClass("android/view/DisplayInfo");
    logical_width_field_ = display_info_class.GetFieldId("logicalWidth", "I");
    logical_height_field_ = display_info_class.GetFieldId("logicalHeight", "I");
    logical_density_dpi_field_ = display_info_class.GetFieldId("logicalDensityDpi", "I");
    rotation_field_ = display_info_class.GetFieldId("rotation", "I");
    layer_stack_field_ = display_info_class.GetFieldId("layerStack", "I");
    flags_field_ = display_info_class.GetFieldId("flags", "I");
    type_field_ = display_info_class.GetFieldId("type", "I");
    state_field_ = display_info_class.GetFieldId("state", "I");

    if (Agent::feature_level() >= 29) {
      display_listener_dispatcher_ = new DisplayListenerDispatcher();
    }

    if (Agent::feature_level() >= 34) {
      display_manager_class_ = jni.GetClass("android/hardware/display/DisplayManager");
      create_virtual_display_method_ = display_manager_class_.GetStaticMethod(
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
    // Null result means that the display no longer exists.
    return DisplayInfo();
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
  int type = display_info.GetIntField(type_field_);
  int state = display_info.GetIntField(state_field_);
  return DisplayInfo(logical_width, logical_height, logical_density_dpi, rotation, layer_stack, flags, type, state);
}

vector<int32_t> DisplayManager::GetDisplayIds(Jni jni) {
  InitializeStatics(jni);
  JObject display_ids = display_manager_global_.CallObjectMethod(jni, get_display_ids_method_);
  auto id_array = static_cast<jintArray>(display_ids.ref());
  jsize size = jni->GetArrayLength(id_array);
  jboolean is_copy;
  jint* ids = jni->GetIntArrayElements(id_array, &is_copy);
  vector<int32_t> result(ids, ids + size);
  jni->ReleaseIntArrayElements(id_array, ids, 0);
  return result;
}

void DisplayManager::AddDisplayListener(Jni jni, DisplayListener* listener) {
  InitializeStatics(jni);
  if (display_listener_dispatcher_ == nullptr) {
    return;
  }

  if (display_listeners_.Add(listener) == 1) {
    display_listener_dispatcher_->Start();
  }
}

void DisplayManager::RemoveDisplayListener(DisplayListener* listener) {
  {
    unique_lock lock(static_initialization_mutex);
    if (display_listener_dispatcher_ == nullptr) {
      return;
    }
  }

  if (display_listeners_.Remove(listener) == 0) {
    display_listener_dispatcher_->Stop();
  }
}

void DisplayManager::RemoveAllDisplayListeners(Jni jni) {
  {
    unique_lock lock(static_initialization_mutex);
    if (display_listener_dispatcher_ == nullptr) {
      return;
    }
  }

  display_listeners_.Clear();
  display_listener_dispatcher_->Stop();
}

void DisplayManager::OnDisplayAdded(Jni jni, int32_t display_id) {
  InitializeStatics(jni);
  Log::D("DisplayManager::OnDisplayAdded %d", display_id);
  display_listeners_.ForEach([display_id](auto listener) {
    listener->OnDisplayAdded(display_id);
  });
}

void DisplayManager::OnDisplayRemoved(Jni jni, int32_t display_id) {
  InitializeStatics(jni);
  Log::D("DisplayManager::OnDisplayRemoved %d", display_id);
  display_listeners_.ForEach([display_id](auto listener) {
    listener->OnDisplayRemoved(display_id);
  });
}

void DisplayManager::OnDisplayChanged(Jni jni, int32_t display_id) {
  InitializeStatics(jni);
  Log::D("DisplayManager::OnDisplayChanged %d", display_id);
  display_listeners_.ForEach([display_id](auto listener) {
    listener->OnDisplayChanged(display_id);
  });
}

VirtualDisplay DisplayManager::CreateVirtualDisplay(
    Jni jni, const char* name, int32_t width, int32_t height, int32_t display_id, ANativeWindow* surface) {
  InitializeStatics(jni);
  return VirtualDisplay(jni, display_manager_class_.CallStaticObjectMethod(
      jni, create_virtual_display_method_, JString(jni, name).ref(), width, height, display_id, SurfaceToJava(jni, surface).ref()));
}

JClass DisplayManager::display_manager_global_class_;
JObject DisplayManager::display_manager_global_;
jmethodID DisplayManager::get_display_info_method_ = nullptr;
jmethodID DisplayManager::get_display_ids_method_ = nullptr;
jfieldID DisplayManager::logical_width_field_ = nullptr;
jfieldID DisplayManager::logical_height_field_ = nullptr;
jfieldID DisplayManager::logical_density_dpi_field_ = nullptr;
jfieldID DisplayManager::rotation_field_ = nullptr;
jfieldID DisplayManager::layer_stack_field_ = nullptr;
jfieldID DisplayManager::flags_field_ = nullptr;
jfieldID DisplayManager::type_field_ = nullptr;
jfieldID DisplayManager::state_field_ = nullptr;
JClass DisplayManager::display_manager_class_;
jmethodID DisplayManager::create_virtual_display_method_ = nullptr;
ConcurrentList<DisplayManager::DisplayListener> DisplayManager::display_listeners_;
DisplayListenerDispatcher* DisplayManager::display_listener_dispatcher_ = nullptr;

}  // namespace screensharing
