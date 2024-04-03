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
#include <set>

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
  [[nodiscard]] static std::string GetSupportedStates();

  [[nodiscard]] static int32_t GetDeviceState(Jni jni);

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

  static std::mutex state_mutex_;
  static int32_t base_state_;  // GUARDED_BY(state_mutex_)
  static int32_t current_state_;  // GUARDED_BY(state_mutex_)
  static bool state_overridden_;  // GUARDED_BY(state_mutex_)

  DISALLOW_COPY_AND_ASSIGN(DeviceStateManager);
};

class DeviceState {
public:
  DeviceState();
  explicit DeviceState(const JObject& device_state);
  explicit DeviceState(int32_t identifier);

  DeviceState& operator=(DeviceState&& other) noexcept;

  [[nodiscard]] int32_t identifier() const { return identifier_; }
  [[nodiscard]] const std::string& name() const;
  [[nodiscard]] bool HasSystemProperty(int32_t property) const { return system_properties_.count(property) != 0; }
  [[nodiscard]] bool HasPhysicalProperty(int32_t property) const { return physical_properties_.count(property) != 0; }

private:
  static std::mutex static_initialization_mutex_;
  static bool statics_initialized_;  // GUARDED_BY(static_initialization_mutex_)
  // DeviceState class.
  static jmethodID get_identifier_method_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID get_name_method_;  // GUARDED_BY(static_initialization_mutex_)
  static jmethodID get_configuration_method_;  // GUARDED_BY(static_initialization_mutex_)
  // DeviceState.Configuration class.
  static jfieldID system_properties_field_;  // GUARDED_BY(static_initialization_mutex_)
  static jfieldID physical_properties_field_;  // GUARDED_BY(static_initialization_mutex_)

  int32_t identifier_;
  std::string name_;
  std::set<int32_t> system_properties_;
  std::set<int32_t> physical_properties_;

  DISALLOW_COPY_AND_ASSIGN(DeviceState);
};

class DeviceStateInfo {
public:
  explicit DeviceStateInfo(const JObject& device_state_info);

  [[nodiscard]] const DeviceState& base_state() { return base_state_; }
  [[nodiscard]] const DeviceState& current_state() { return current_state_; }
  [[nodiscard]] int32_t base_state_identifier() const { return base_state_.identifier(); }
  [[nodiscard]] int32_t current_state_identifier() const { return current_state_.identifier(); }

private:
  static std::mutex static_initialization_mutex_;
  static bool statics_initialized_;  // GUARDED_BY(static_initialization_mutex_)
  static jfieldID base_state_field_;  // GUARDED_BY(static_initialization_mutex_)
  static jfieldID current_state_field_;  // GUARDED_BY(static_initialization_mutex_)

  DeviceState base_state_;
  DeviceState current_state_;

  DISALLOW_COPY_AND_ASSIGN(DeviceStateInfo);
};

}  // namespace screensharing
