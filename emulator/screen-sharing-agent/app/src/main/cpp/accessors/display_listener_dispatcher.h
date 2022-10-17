/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <future>
#include <mutex>
#include <thread>

#include "accessors/display_manager.h"
#include "jvm.h"

namespace screensharing {

// Provides access to the android.hardware.display.IDisplayListener.getDisplayInfo method.
class DisplayListenerDispatcher {
public:
  ~DisplayListenerDispatcher();
  void Start();
  void Stop();

private:
  friend class DisplayManager;

  DisplayListenerDispatcher(const JClass& display_manager_class, const JObject& display_manager);
  void Run();

  const JClass& display_manager_class_;
  const JObject& display_manager_;
  Jni jni_ = nullptr;
  std::mutex mutex_;
  std::thread thread_;  // GUARDED_BY(mutex_)
  std::promise<JObject> looper_promise_; // GUARDED_BY(mutex_)

  DISALLOW_COPY_AND_ASSIGN(DisplayListenerDispatcher);
};

}  // namespace screensharing
