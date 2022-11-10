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

class DisplayListenerDispatcher;

// Provides access to the android.hardware.display.IDisplayManager.getDisplayInfo method.
class DisplayManager {
public:
  struct DisplayListener {
    virtual void OnDisplayAdded(int32_t display_id) = 0;

    virtual void OnDisplayRemoved(int32_t display_id) = 0;

    virtual void OnDisplayChanged(int32_t display_id) = 0;
  };

  static DisplayInfo GetDisplayInfo(Jni jni, int32_t display_id);
  static void RegisterDisplayListener(Jni jni, DisplayListener* listener);
  static void UnregisterDisplayListener(Jni jni, DisplayListener* listener);

  static void OnDisplayAdded(Jni jni, int32_t display_id);
  static void OnDisplayChanged(Jni jni, int32_t display_id);
  static void OnDisplayRemoved(Jni jni, int32_t display_id);

private:
  DisplayManager(Jni jni);
  ~DisplayManager();
  static DisplayManager& GetInstance(Jni jni);

  JClass display_manager_class_;
  JObject display_manager_;
  jmethodID get_display_info_method_;
  jfieldID logical_width_field_;
  jfieldID logical_height_field_;
  jfieldID rotation_field_;
  jfieldID layer_stack_field_;
  jfieldID flags_field_;
  // Copy-on-write set of clipboard listeners.
  std::atomic<std::vector<DisplayListener*>*> display_listeners_;

  DisplayListenerDispatcher* display_listener_dispatcher_;  // Owned

  DISALLOW_COPY_AND_ASSIGN(DisplayManager);
};

}  // namespace screensharing
