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

#include <map>
#include <mutex>

#include "common.h"
#include "concurrent_list.h"
#include "jvm.h"

namespace screensharing {

// Provides access to the android.view.IWindowManager methods.
class WindowManager {
public:
  struct RotationWatcher {
    virtual void OnRotationChanged(int rotation) = 0;
  };

  static void FreezeRotation(Jni jni, int32_t display_id, int32_t rotation);
  static void ThawRotation(Jni jni, int32_t display_id);
  static bool IsRotationFrozen(Jni jni, int32_t display_id);
  static int32_t WatchRotation(Jni jni, int32_t display_id, RotationWatcher* watcher);
  static void RemoveRotationWatcher(Jni jni, int32_t display_id, RotationWatcher* watcher);

  static void OnRotationChanged(int32_t display_id, int32_t rotation);

private:
  // Tracks rotation of a single display.
  struct DisplayRotationTracker {
    DisplayRotationTracker();

    JObject watcher_adapter;
    // List of display rotation watchers.
    ConcurrentList<RotationWatcher> rotation_watchers;
    std::atomic_int32_t rotation;

    DISALLOW_COPY_AND_ASSIGN(DisplayRotationTracker);
  };

  WindowManager() = delete;

  static void InitializeStatics(Jni jni);

  // WindowManager class.
  static JObject window_manager_;
  static JClass window_manager_class_;
  static jmethodID freeze_display_rotation_method_;
  static bool freeze_display_rotation_method_requires_attribution_tag_;
  static jmethodID thaw_display_rotation_method_;
  static jmethodID is_display_rotation_frozen_method_;
  static jmethodID watch_rotation_method_;
  // RotationWatcher class.
  static JClass rotation_watcher_class_;
  static jmethodID rotation_watcher_constructor_;

  static std::mutex mutex_;
  // Display rotation trackers keyed by display IDs.
  static std::map<int32_t, DisplayRotationTracker> rotation_trackers_;  // GUARDED_BY(mutex_)
};

}  // namespace screensharing
