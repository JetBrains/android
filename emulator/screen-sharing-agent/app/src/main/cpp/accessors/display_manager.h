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

#pragma once

#include "display_info.h"
#include "jvm.h"

namespace screensharing {

constexpr int32_t DEFAULT_DISPLAY = 0; // See android.view.Display.DEFAULT_DISPLAY

// Provides access to the android.hardware.display.IDisplayManager.getDisplayInfo method.
class DisplayManager {
public:
  static DisplayInfo GetDisplayInfo(Jni jni, int32_t display_id);

private:
  DisplayManager(Jni jni);
  static DisplayManager& GetInstance(Jni jni);

  static DisplayManager* instance_;

  JObject display_manager_;
  jmethodID get_display_info_method_;
  jfieldID logical_width_field_;
  jfieldID logical_height_field_;
  jfieldID rotation_field_;
  jfieldID layer_stack_field_;
  jfieldID flags_field_;

  DISALLOW_COPY_AND_ASSIGN(DisplayManager);
};

}  // namespace screensharing
