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

#include "base128_output_stream.h"
#include "jvm.h"

namespace screensharing {

// Similar to the `android.hardware.devicestate.DeviceState` class.
class DeviceState {
public:
  enum Property : uint32_t {
    PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_CLOSED = 1 << 0,
    PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_HALF_OPEN = 1 << 1,
    PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_OPEN = 1 << 2,
    PROPERTY_POLICY_CANCEL_OVERRIDE_REQUESTS = 1 << 3,
    PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP = 1 << 4,
    PROPERTY_POLICY_UNSUPPORTED_WHEN_THERMAL_STATUS_CRITICAL = 1 << 5,
    PROPERTY_POLICY_UNSUPPORTED_WHEN_POWER_SAVE_MODE = 1 << 6,
    PROPERTY_POLICY_AVAILABLE_FOR_APP_REQUEST = 1 << 7,
    PROPERTY_APP_INACCESSIBLE = 1 << 8,
    PROPERTY_EMULATED_ONLY = 1 << 9,
    PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_OUTER_PRIMARY = 1 << 10,
    PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_INNER_PRIMARY = 1 << 11,
    PROPERTY_POWER_CONFIGURATION_TRIGGER_SLEEP = 1 << 12,
    PROPERTY_POWER_CONFIGURATION_TRIGGER_WAKE = 1 << 13,
    PROPERTY_EXTENDED_DEVICE_STATE_EXTERNAL_DISPLAY = 1 << 14,
    PROPERTY_FEATURE_REAR_DISPLAY = 1 << 15,
    PROPERTY_FEATURE_DUAL_DISPLAY_INTERNAL_DEFAULT = 1 << 16,
  };

  explicit DeviceState(const JObject& device_state);
  DeviceState(int32_t identifier, std::string name, uint32_t system_properties, uint32_t physical_properties);
  DeviceState(DeviceState&& other) noexcept;

  DeviceState& operator=(DeviceState&& other) noexcept;

  void Serialize(Base128OutputStream& stream) const;

  [[nodiscard]] int32_t identifier() const { return identifier_; }
  [[nodiscard]] const std::string& name() const { return name_; }
  [[nodiscard]] const uint32_t& system_properties() const { return system_properties_; }
  [[nodiscard]] const uint32_t& physical_properties() const { return physical_properties_; }

  static int32_t GetIdentifier(const JObject& device_state);

private:
  static void InitializeStatics(const JObject& device_state) ;

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
  uint32_t system_properties_ = 0;
  uint32_t physical_properties_ = 0;

  DISALLOW_COPY_AND_ASSIGN(DeviceState);
};

}  // namespace screensharing
