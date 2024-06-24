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

#include <mutex>

#include "common.h"
#include "scoped_setting.h"

namespace screensharing {

// Manages system settings that are modified when the agent starts and restored to the original
// values when the agent terminates.
class SessionEnvironment {
public:
  SessionEnvironment(bool turn_off_display);
  ~SessionEnvironment();

  // Turns off "Auto-rotate screen".
  void DisableAccelerometerRotation() {
    accelerometer_rotation_.Set("0");
  }
  // Restores the original "Auto-rotate screen" setting.
  void RestoreAccelerometerRotation() {
    accelerometer_rotation_.Restore();
  }

private:
  ScopedSetting accelerometer_rotation_;
  ScopedSetting stay_on_;
  bool restore_normal_display_power_mode_;

  DISALLOW_COPY_AND_ASSIGN(SessionEnvironment);
};

}  // namespace screensharing
