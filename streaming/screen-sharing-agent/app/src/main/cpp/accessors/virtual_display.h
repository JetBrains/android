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

#pragma once

#include <android/native_window.h>

#include "display_info.h"
#include "jvm.h"

namespace screensharing {

class VirtualDisplay : private JObject {
public:
  using JObject::JObject;
  VirtualDisplay(JObject&& virtual_display);

  ~VirtualDisplay();

  VirtualDisplay& operator=(VirtualDisplay&& other);

  void Resize(int32_t width, int32_t height, int32_t density_dpi);
  void SetSurface(ANativeWindow* surface);
  void ReleaseDisplay();
  void ReleaseDisplay(Jni jni);

private:
  static jmethodID set_surface_method_;
  static jmethodID resize_method_;
  static jmethodID release_method_;

  DISALLOW_COPY_AND_ASSIGN(VirtualDisplay);
};

}  // namespace screensharing
