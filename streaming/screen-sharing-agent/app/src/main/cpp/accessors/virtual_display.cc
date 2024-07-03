/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "virtual_display.h"

#include "jvm.h"
#include "surface.h"

namespace screensharing {

using namespace std;

VirtualDisplay::VirtualDisplay(JObject&& virtual_display)
    : JObject(std::move(virtual_display)) {
  if (set_surface_method_ == nullptr) {
    JClass virtual_display_class = GetClass();
    set_surface_method_ = virtual_display_class.GetMethod("setSurface", "(Landroid/view/Surface;)V");
    resize_method_ = virtual_display_class.GetMethod("resize", "(III)V");
    release_method_ = virtual_display_class.GetMethod("release", "()V");
  }
}

VirtualDisplay::~VirtualDisplay() {
  ReleaseDisplay();
}

VirtualDisplay& VirtualDisplay::operator=(VirtualDisplay&& other) noexcept {
  ReleaseDisplay();
  JObject::operator=(static_cast<JObject&&>(other));
  return *this;
}

void VirtualDisplay::ReleaseDisplay() {
  if (IsNotNull()) {
    CallVoidMethod(GetJni(), release_method_);
    Release();
  }
}

void VirtualDisplay::ReleaseDisplay(Jni jni) {
  if (IsNotNull()) {
    CallVoidMethod(jni, release_method_);
    Release();
  }
}

void VirtualDisplay::Resize(int32_t width, int32_t height, int32_t density_dpi) {
  CallVoidMethod(resize_method_, width, height, density_dpi);
}

void VirtualDisplay::SetSurface(ANativeWindow* surface) {
  CallVoidMethod(set_surface_method_, SurfaceToJava(GetJni(), surface).ref());
}

jmethodID VirtualDisplay::set_surface_method_ = nullptr;
jmethodID VirtualDisplay::resize_method_ = nullptr;
jmethodID VirtualDisplay::release_method_ = nullptr;

}  // namespace screensharing
