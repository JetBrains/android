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

#include <memory>
#include <set>

#include "common.h"
#include "jvm.h"

namespace screensharing {

// Provides access to the android.view.IWindowManager methods.
class WindowManager {
public:
  struct RotationWatcher {
    virtual void OnRotationChanged(int rotation) = 0;
  };

  ~WindowManager();

  static int GetDefaultDisplayRotation(Jni jni);
  static void FreezeRotation(Jni jni, int32_t rotation);
  static void ThawRotation(Jni jni);
  static bool IsRotationFrozen(Jni jni);
  static int32_t WatchRotation(Jni jni, RotationWatcher* watcher);
  static void RemoveRotationWatcher(Jni jni, RotationWatcher* watcher);

  void OnRotationChanged(int32_t rotation);

private:
  WindowManager(Jni jni);
  static WindowManager& GetInstance(Jni jni);

  JObject window_manager_;
  jmethodID get_default_display_rotation_method_;
  jmethodID freeze_rotation_method_;
  jmethodID thaw_rotation_method_;
  jmethodID is_rotation_frozen_method_;
  std::atomic_int32_t rotation_;
  JObject watcher_object_;
  // Copy-on-write set of display_rotation watchers.
  std::atomic<std::set<RotationWatcher*>*> rotation_watchers_;

  DISALLOW_COPY_AND_ASSIGN(WindowManager);
};

}  // namespace screensharing
