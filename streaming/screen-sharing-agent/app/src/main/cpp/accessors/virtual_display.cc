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
#include "log.h"
#include "surface.h"

namespace screensharing {

using namespace std;

VirtualDisplay::VirtualDisplay(Jni jni, JObject&& virtual_display)
    : jni_(jni),
      virtual_display_(std::move(virtual_display)) {
  JClass virtual_display_class = virtual_display_.GetClass();
  if (set_surface_method_ == nullptr) {
    set_surface_method_ = virtual_display_class.GetMethodId("setSurface", "(Landroid/view/Surface;)V");
    resize_method_ = virtual_display_class.GetMethodId("resize", "(III)V");
    release_method_ = virtual_display_class.GetMethodId("release", "()V");
  }
}

VirtualDisplay::~VirtualDisplay() {
  Release();
}

void VirtualDisplay::Release() {
  if (virtual_display_.IsNotNull()) {
    virtual_display_.CallVoidMethod(release_method_);
    virtual_display_.Release();
  }
}

void VirtualDisplay::Resize(int32_t width, int32_t height, int32_t density_dpi) {
  virtual_display_.CallVoidMethod(resize_method_, width, height, density_dpi);
}

void VirtualDisplay::SetSurface(ANativeWindow* surface) {
  virtual_display_.CallVoidMethod(set_surface_method_, SurfaceToJava(jni_, surface).ref());
}

jmethodID VirtualDisplay::set_surface_method_ = nullptr;
jmethodID VirtualDisplay::resize_method_ = nullptr;
jmethodID VirtualDisplay::release_method_ = nullptr;

}  // namespace screensharing
