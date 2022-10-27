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

#include <android/native_window_jni.h>

#include <mutex>

#include "log.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex; // Protects initialization of static fields.

SurfaceControl::SurfaceControl(Jni jni)
    : jni_(jni) {
  InitializeStatics(jni);
}

SurfaceControl::~SurfaceControl() = default;

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

JObject SurfaceControl::GetInternalDisplayToken() const {
  int api_level = android_get_device_api_level();
  {
    scoped_lock lock(static_initialization_mutex);
    if (get_internal_display_token_method_ == nullptr) {
      get_internal_display_token_method_ = api_level >= 29 ?
          surface_control_class_.GetStaticMethodId(jni_, "getInternalDisplayToken", "()Landroid/os/IBinder;") :
          surface_control_class_.GetStaticMethodId(jni_, "getBuiltInDisplay", "(I)Landroid/os/IBinder;");
    }
  }
  return api_level >= 29 ?
      surface_control_class_.CallStaticObjectMethod(jni_, get_internal_display_token_method_) :
      surface_control_class_.CallStaticObjectMethod(jni_, get_internal_display_token_method_, 0);
}

void SurfaceControl::OpenTransaction() const {
  surface_control_class_.CallStaticVoidMethod(jni_, open_transaction_method_);
}

void SurfaceControl::CloseTransaction() const {
  surface_control_class_.CallStaticVoidMethod(jni_, close_transaction_method_);
}

JObject SurfaceControl::CreateDisplay(const char* name, bool secure) const {
  return surface_control_class_.CallStaticObjectMethod(jni_, create_display_method_, JString(jni_, name).ref(), jboolean(secure));
}

void SurfaceControl::DestroyDisplay(jobject display_token) const {
  surface_control_class_.CallStaticObjectMethod(jni_, destroy_display_method_, display_token);
}

void SurfaceControl::SetDisplaySurface(jobject display_token, ANativeWindow* surface) const {
  JObject java_surface(jni_, ANativeWindow_toSurface(jni_, surface));
  if (java_surface.IsNull()) {
    Log::Fatal("Unable to create an android.view.Surface");
  }
  surface_control_class_.CallStaticObjectMethod(jni_, set_display_surface_method_, display_token, java_surface.ref());
}

void SurfaceControl::SetDisplayLayerStack(jobject display_token, int32_t layer_stack) const {
  surface_control_class_.CallStaticObjectMethod(
      jni_, set_display_layer_stack_method_, display_token, static_cast<jint>(layer_stack));
}

void SurfaceControl::SetDisplayProjection(
    jobject display_token, int32_t orientation, const ARect& layer_stack_rect, const ARect& display_rect) const {
  Log::D("SurfaceControl::SetDisplayProjection: layer_stack_rect=%dx%d, display_rect=%dx%d",
         layer_stack_rect.right, layer_stack_rect.bottom, display_rect.right, display_rect.bottom);
  JObject java_layer_stack_rect = ToJava(layer_stack_rect);
  JObject java_display_rect = ToJava(display_rect);
  surface_control_class_.CallStaticObjectMethod(
      jni_, set_display_projection_method_, display_token, static_cast<jint>(orientation),
      java_layer_stack_rect.ref(), java_display_rect.ref());
}

void SurfaceControl::SetDisplayPowerMode(jobject display_token, DisplayPowerMode mode) {
  {
    scoped_lock lock(static_initialization_mutex);
    if (set_display_power_mode_method_ == nullptr) {
      set_display_power_mode_method_ = surface_control_class_.GetStaticMethodId(jni_, "setDisplayPowerMode", "(Landroid/os/IBinder;I)V");
    }
  }
  surface_control_class_.CallStaticVoidMethod(jni_, set_display_power_mode_method_, display_token, mode);
}

JObject SurfaceControl::ToJava(const ARect& rect) const {
  return rect_class_.NewObject(
      jni_, rect_constructor_, static_cast<jint>(rect.left), static_cast<jint>(rect.top),
      static_cast<jint>(rect.right), static_cast<jint>(rect.bottom));
}

JClass SurfaceControl::surface_control_class_;
jmethodID SurfaceControl::get_internal_display_token_method_ = nullptr;
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