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

#include "session_environment.h"

#include "accessors/surface_control.h"
#include "num_to_string.h"
#include "settings.h"

namespace screensharing {

// Constants from android.os.BatteryManager.
constexpr int BATTERY_PLUGGED_AC = 1;
constexpr int BATTERY_PLUGGED_USB = 2;
constexpr int BATTERY_PLUGGED_WIRELESS = 4;

using namespace std;

SessionEnvironment::SessionEnvironment(bool turn_off_display)
    : accelerometer_rotation_(Settings::Table::SYSTEM, "accelerometer_rotation"),
      stay_on_(Settings::Table::GLOBAL, "stay_on_while_plugged_in"),
      restore_normal_display_power_mode_(false) {
  // Turn off "Auto-rotate screen".
  accelerometer_rotation_.Set("0");
  // Keep the screen on as long as the device has power.
  stay_on_.Set(num_to_string<BATTERY_PLUGGED_AC | BATTERY_PLUGGED_USB | BATTERY_PLUGGED_WIRELESS>::value);

  if (turn_off_display) {
    // Turn off display.
    SurfaceControl surface_control(Jvm::GetJni());
    JObject display_token = surface_control.GetInternalDisplayToken();
    if (!display_token.IsNull()) {
      surface_control.SetDisplayPowerMode(display_token, DisplayPowerMode::POWER_MODE_OFF);
      restore_normal_display_power_mode_ = true;
    }
  }
}

SessionEnvironment::~SessionEnvironment() {
  if (restore_normal_display_power_mode_) {
    SurfaceControl surface_control(Jvm::GetJni());
    JObject display_token = surface_control.GetInternalDisplayToken();
    if (!display_token.IsNull()) {
      surface_control.SetDisplayPowerMode(display_token, DisplayPowerMode::POWER_MODE_NORMAL);
    }
  }
}

}  // namespace screensharing
