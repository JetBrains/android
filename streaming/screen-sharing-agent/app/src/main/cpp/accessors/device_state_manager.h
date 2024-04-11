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
#include <vector>

#include "concurrent_list.h"
#include "device_state.h"
#include "jvm.h"

namespace screensharing {

// Provides access to the "device_state" service.
class DeviceStateManager {
public:
  // See android.hardware.devicestate.DeviceStateManager.INVALID_DEVICE_STATE_IDENTIFIER.
  static constexpr int32_t INVALID_DEVICE_STATE_IDENTIFIER = -1;

  struct DeviceStateListener {
    virtual void OnDeviceStateChanged(int32_t device_state) = 0;
  };

  [[nodiscard]] static const std::vector<DeviceState>& GetSupportedDeviceStates(Jni jni);

  [[nodiscard]] static int32_t GetDeviceStateIdentifier(Jni jni);

  static void RequestState(Jni jni, int32_t state_id, int32_t flags);

  static void AddDeviceStateListener(DeviceStateListener* listener);
  static void RemoveDeviceStateListener(DeviceStateListener* listener);

  static void OnDeviceStateChanged(Jni jni, const JObject& device_state_info);

  DeviceStateManager() = delete;

private:
  // Initializes the static fields of this class. The return value indicates whether
  // DeviceStateManager is available or not. */
  static bool InitializeStatics(Jni jni);

  static void NotifyListeners(int32_t device_state);

  static std::mutex static_initialization_mutex_;
  static bool statics_initialized_;  // GUARDED_BY(static_initialization_mutex_)
  // DeviceStateManager class.
  static JObject device_state_manager_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID get_device_state_info_method_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID request_state_method_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID cancel_state_request_method_;  // GUARDED_BY(static_initialization_mutex_)
  // Binder class.
  static JClass binder_class_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID binder_constructor_;  // GUARDED_BY(static_initialization_mutex_)
  // List of device state listeners.
  static ConcurrentList<DeviceStateListener> device_state_listeners_;

  static std::vector<DeviceState> supported_device_states_;
  static std::mutex state_mutex_;
  static int32_t base_state_identifier_;  // GUARDED_BY(state_mutex_)
  static int32_t current_state_identifier_;  // GUARDED_BY(state_mutex_)
  static bool state_overridden_;  // GUARDED_BY(state_mutex_)

  DISALLOW_COPY_AND_ASSIGN(DeviceStateManager);
};

}  // namespace screensharing
