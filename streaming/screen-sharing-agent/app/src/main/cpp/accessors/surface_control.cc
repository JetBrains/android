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

#include "surface_control.h"

#include <mutex>

#include "log.h"
#include "surface.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex; // Protects initialization of static fields.

void SurfaceControl::InitializeStatics(Jni jni) {
  scoped_lock lock(static_initialization_mutex);

  if (surface_control_class_.IsNull()) {
    surface_control_class_ = jni.GetClass("android/view/SurfaceControl");
    close_transaction_method_ = surface_control_class_.GetStaticMethodId("closeTransaction", "()V");
    open_transaction_method_ = surface_control_class_.GetStaticMethodId("openTransaction", "()V");
    create_display_method_ = surface_control_class_.GetStaticMethodId("createDisplay", "(Ljava/lang/String;Z)Landroid/os/IBinder;");
    destroy_display_method_ = surface_control_class_.GetStaticMethodId("destroyDisplay", "(Landroid/os/IBinder;)V");
    set_display_surface_method_ = surface_control_class_.GetStaticMethodId(
        "setDisplaySurface", "(Landroid/os/IBinder;Landroid/view/Surface;)V");
    set_display_layer_stack_method_ = surface_control_class_.GetStaticMethodId("setDisplayLayerStack", "(Landroid/os/IBinder;I)V");
    set_display_projection_method_ = surface_control_class_.GetStaticMethodId(
        "setDisplayProjection", "(Landroid/os/IBinder;ILandroid/graphics/Rect;Landroid/graphics/Rect;)V");
    rect_class_ = jni.GetClass("android/graphics/Rect");
    rect_constructor_ = rect_class_.GetConstructorId("(IIII)V");

    surface_control_class_.MakeGlobal();
    rect_class_.MakeGlobal();
  }
}

JObject SurfaceControl::GetInternalDisplayToken(Jni jni) {
  InitializeStatics(jni);
  int api_level = android_get_device_api_level();
  {
    scoped_lock lock(static_initialization_mutex);
    if (get_internal_display_token_method_not_available_) {
      return JObject();
    }
    if (get_internal_display_token_method_ == nullptr) {
      get_internal_display_token_method_ =
          api_level >= 33 ?
              surface_control_class_.FindStaticMethod(jni, "getInternalDisplayToken", "()Landroid/os/IBinder;") :
          api_level >= 29 ?
              surface_control_class_.GetStaticMethodId(jni, "getInternalDisplayToken", "()Landroid/os/IBinder;") :
              surface_control_class_.GetStaticMethodId(jni, "getBuiltInDisplay", "(I)Landroid/os/IBinder;");
      if (get_internal_display_token_method_ == nullptr) {
        get_internal_display_token_method_not_available_ = true;
        return JObject();
      }
    }
  }
  return api_level >= 29 ?
      surface_control_class_.CallStaticObjectMethod(jni, get_internal_display_token_method_) :
      surface_control_class_.CallStaticObjectMethod(jni, get_internal_display_token_method_, 0);
}

void SurfaceControl::OpenTransaction(Jni jni) {
  surface_control_class_.CallStaticVoidMethod(jni, open_transaction_method_);
}

void SurfaceControl::CloseTransaction(Jni jni) {
  surface_control_class_.CallStaticVoidMethod(jni, close_transaction_method_);
}

JObject SurfaceControl::CreateDisplay(Jni jni, const char* name, bool secure) {
  InitializeStatics(jni);
  return surface_control_class_.CallStaticObjectMethod(jni, create_display_method_, JString(jni, name).ref(), jboolean(secure));
}

void SurfaceControl::DestroyDisplay(Jni jni, jobject display_token) {
  InitializeStatics(jni);
  surface_control_class_.CallStaticObjectMethod(jni, destroy_display_method_, display_token);
}

void SurfaceControl::SetDisplaySurface(Jni jni, jobject display_token, ANativeWindow* surface) {
  surface_control_class_.CallStaticObjectMethod(jni, set_display_surface_method_, display_token, SurfaceToJava(jni, surface).ref());
}

void SurfaceControl::SetDisplayLayerStack(Jni jni, jobject display_token, int32_t layer_stack) {
  surface_control_class_.CallStaticObjectMethod(jni, set_display_layer_stack_method_, display_token, static_cast<jint>(layer_stack));
}

void SurfaceControl::SetDisplayProjection(
    Jni jni, jobject display_token, int32_t orientation, const ARect& layer_stack_rect, const ARect& display_rect) {
  Log::D("SurfaceControl::SetDisplayProjection: layer_stack_rect=%dx%d, display_rect=%dx%d",
         layer_stack_rect.right, layer_stack_rect.bottom, display_rect.right, display_rect.bottom);
  JObject java_layer_stack_rect = ToJava(jni, layer_stack_rect);
  JObject java_display_rect = ToJava(jni, display_rect);
  surface_control_class_.CallStaticObjectMethod(
      jni, set_display_projection_method_, display_token, static_cast<jint>(orientation),
      java_layer_stack_rect.ref(), java_display_rect.ref());
}

void SurfaceControl::ConfigureProjection(
    Jni jni, jobject display_token, ANativeWindow* surface, const DisplayInfo& display_info, Size projected_size) {
  struct Transaction {
    explicit Transaction(Jni jni)
        : jni_(jni) {
      OpenTransaction(jni_);
    }
    ~Transaction() {
      CloseTransaction(jni_);
    }

    Jni jni_;
  };
  InitializeStatics(jni);
  Transaction transaction(jni);
  SetDisplaySurface(jni, display_token, surface);
  SetDisplayProjection(jni, display_token, 0, display_info.logical_size.toRect(), projected_size.toRect());
  SetDisplayLayerStack(jni, display_token, display_info.layer_stack);
}

void SurfaceControl::SetDisplayPowerMode(Jni jni, jobject display_token, DisplayPowerMode mode) {
  InitializeStatics(jni);
  {
    scoped_lock lock(static_initialization_mutex);
    if (set_display_power_mode_method_ == nullptr) {
      set_display_power_mode_method_ = surface_control_class_.GetStaticMethodId(jni, "setDisplayPowerMode", "(Landroid/os/IBinder;I)V");
    }
  }
  surface_control_class_.CallStaticVoidMethod(jni, set_display_power_mode_method_, display_token, mode);
}

JObject SurfaceControl::ToJava(Jni jni, const ARect& rect) {
  return rect_class_.NewObject(
      jni, rect_constructor_, static_cast<jint>(rect.left), static_cast<jint>(rect.top),
      static_cast<jint>(rect.right), static_cast<jint>(rect.bottom));
}

JClass SurfaceControl::surface_control_class_;
jmethodID SurfaceControl::get_internal_display_token_method_ = nullptr;
bool SurfaceControl::get_internal_display_token_method_not_available_ = false;
jmethodID SurfaceControl::close_transaction_method_ = nullptr;
jmethodID SurfaceControl::open_transaction_method_ = nullptr;
jmethodID SurfaceControl::create_display_method_ = nullptr;
jmethodID SurfaceControl::destroy_display_method_ = nullptr;
jmethodID SurfaceControl::set_display_surface_method_ = nullptr;
jmethodID SurfaceControl::set_display_layer_stack_method_ = nullptr;
jmethodID SurfaceControl::set_display_projection_method_ = nullptr;
jmethodID SurfaceControl::set_display_power_mode_method_ = nullptr;
JClass SurfaceControl::rect_class_;
jmethodID SurfaceControl::rect_constructor_ = nullptr;

} // namespace screensharing