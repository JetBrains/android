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

#include <android/native_window.h>

#include "accessors/display_info.h"
#include "accessors/virtual_display.h"
#include "concurrent_list.h"
#include "jvm.h"

namespace screensharing {

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
  static std::vector<int32_t> GetDisplayIds(Jni jni);
  static void AddDisplayListener(Jni jni, DisplayListener* listener);
  static void RemoveDisplayListener(DisplayListener* listener);
  static void RemoveAllDisplayListeners(Jni jni);

  static void OnDisplayAdded(Jni jni, int32_t display_id);
  static void OnDisplayChanged(Jni jni, int32_t display_id);
  static void OnDisplayRemoved(Jni jni, int32_t display_id);

  // Requires API 34+.
  static VirtualDisplay CreateVirtualDisplay(
      Jni jni, const char* name, int32_t width, int32_t height, int32_t display_id, ANativeWindow* surface);
  // Requests to power a display OFF or reset it to a power state it supposed to have. Requires API 35+.
  // The state parameter is one of DisplayInfo::STATE_OFF (to turn display off), DisplayInfo::STATE_UNKNOWN
  // (to reset the display to its default state). Returns true if successful, false otherwise.
  static bool RequestDisplayPower(Jni jni, int32_t display_id, int state);

  DisplayManager() = delete;

private:
  friend class DisplayListenerDispatcher;

  static void InitializeStatics(Jni jni);

  // DisplayManagerGlobal class.
  static JClass display_manager_global_class_;
  static JObject display_manager_global_;
  static jmethodID get_display_info_method_;
  static jmethodID get_display_ids_method_;
  static jmethodID request_display_power_method_;
  // DisplayInfo class.
  static jfieldID logical_width_field_;
  static jfieldID logical_height_field_;
  static jfieldID logical_density_dpi_field_;
  static jfieldID rotation_field_;
  static jfieldID layer_stack_field_;
  static jfieldID flags_field_;
  static jfieldID type_field_;
  static jfieldID state_field_;
  // DisplayManager class.
  static JClass display_manager_class_;
  static jmethodID create_virtual_display_method_;

  // List of display listeners.
  static ConcurrentList<DisplayListener> display_listeners_;

  static DisplayListenerDispatcher* display_listener_dispatcher_;

  DISALLOW_COPY_AND_ASSIGN(DisplayManager);
};

}  // namespace screensharing
