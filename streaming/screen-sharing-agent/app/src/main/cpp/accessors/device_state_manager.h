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

#include <mutex>
#include <string>
#include <vector>

#include "concurrent_list.h"
#include "jvm.h"

namespace screensharing {

// Provides access to the "device_state" service.
class DeviceStateManager {
public:
  struct DeviceStateListener {
    virtual void OnDeviceStateChanged(int32_t device_state) = 0;
  };

  // Returns a string equivalent to the 'adb shell cmd device_state print-states' command, e.g.
  // "Supported states: [
  //   DeviceState{identifier=0, name='CLOSE', app_accessible=true},
  //   DeviceState{identifier=1, name='TENT', app_accessible=true},
  //   DeviceState{identifier=2, name='HALF_FOLDED', app_accessible=true},
  //   DeviceState{identifier=3, name='OPEN', app_accessible=true},
  // ]"
  static std::string GetSupportedStates();

  static int32_t GetDeviceState(Jni jni);

  static void RequestState(Jni jni, int32_t state_id, int32_t flags);

  static void AddDeviceStateListener(DeviceStateListener* listener);
  static void RemoveDeviceStateListener(DeviceStateListener* listener);

  static void OnDeviceStateChanged(Jni jni, jobject device_state_info);

private:
  DeviceStateManager() = delete;

  // Initializes the static fields of this class. The return value indicates whether
  // DeviceStateManager is available or not. */
  static bool InitializeStatics(Jni jni);

  static void NotifyListeners(int32_t device_state);

  static std::mutex static_initialization_mutex_;
  static bool initialized_;  // GUARDED_BY(static_initialization_mutex_)
  // DeviceStateManager class.
  static JObject device_state_manager_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID get_device_state_info_method_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID request_state_method_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID cancel_state_request_method_;  // GUARDED_BY(static_initialization_mutex_)
  // DeviceStateInfo class.
  static jfieldID base_state_field_;  // GUARDED_BY(static_initialization_mutex_)
  static jfieldID current_state_field_;  // GUARDED_BY(static_initialization_mutex_)
  // Binder class.
  static JClass binder_class_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID binder_constructor_;  // GUARDED_BY(static_initialization_mutex_)
  // List of device state listeners.
  static ConcurrentList<DeviceStateListener> device_state_listeners_;

  static std::mutex state_mutex_;
  static int32_t current_base_state_;  // GUARDED_BY(state_mutex_)
  static int32_t current_state_;  // GUARDED_BY(state_mutex_)
  static bool state_overridden_;  // GUARDED_BY(state_mutex_)

  DISALLOW_COPY_AND_ASSIGN(DeviceStateManager);
};

}  // namespace screensharing
