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

#include "ui_settings.h"

#include "shell_command_executor.h"

namespace screensharing {

using namespace std;

void UiSettings::Get(UiSettingsResponse* response) {
  string value = ExecuteShellCommand("cmd uimode night");
  while (!value.empty() && value.back() <= ' ') {
    value.erase(value.size() - 1);
  }
  response->set_dark_mode(value == "Night mode: yes");
}

void UiSettings::SetDarkMode(bool dark_mode) {
  string command = string("cmd uimode night ") + (dark_mode ? "yes" : "no");
  ExecuteShellCommand(command);
}

}  // namespace screensharing
