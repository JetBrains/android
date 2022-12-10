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
#include "log.h"
#include "num_to_string.h"
#include "settings.h"

namespace screensharing {

using namespace std;

namespace {

// Constants from android.os.BatteryManager.
constexpr int BATTERY_PLUGGED_AC = 1;
constexpr int BATTERY_PLUGGED_USB = 2;
constexpr int BATTERY_PLUGGED_WIRELESS = 4;

// Names an location of the screen sharing agent's files.
#define SCREEN_SHARING_AGENT_JAR_NAME "screen-sharing-agent.jar"
#define SCREEN_SHARING_AGENT_SO_NAME "libscreen-sharing-agent.so"
#define DEVICE_PATH_BASE "/data/local/tmp/.studio"

// Removes files of the screen sharing agent from the persistent storage.
void RemoveAgentFiles() {
  remove(DEVICE_PATH_BASE "/" SCREEN_SHARING_AGENT_JAR_NAME);
  remove(DEVICE_PATH_BASE "/" SCREEN_SHARING_AGENT_SO_NAME);
}

}  // namespace

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

  RemoveAgentFiles();
}

SessionEnvironment::~SessionEnvironment() {
  if (restore_normal_display_power_mode_) {
    SurfaceControl surface_control(Jvm::GetJni());
    JObject display_token = surface_control.GetInternalDisplayToken();
    if (!display_token.IsNull()) {
      surface_control.SetDisplayPowerMode(display_token, DisplayPowerMode::POWER_MODE_NORMAL);
    }
  }
  stay_on_.Restore();
  accelerometer_rotation_.Restore();
  Log::D("Restored original system settings");
}

}  // namespace screensharing
