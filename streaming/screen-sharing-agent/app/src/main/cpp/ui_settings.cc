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

#include <sstream>

#include "string_printf.h"
#include "shell_command_executor.h"

namespace screensharing {

using namespace std;

namespace {

#define DARK_MODE_DIVIDER "-- Dark Mode --"
#define FONT_SIZE_DIVIDER "-- Font Size --"

string TrimEnd(string value) {
  while (!value.empty() && value.back() <= ' ') {
    value.erase(value.size() - 1);
  }
  return value;
}

void ProcessDarkMode(stringstream* stream, UiSettingsResponse* response) {
  string line;
  bool dark_mode = false;
  if (getline(*stream, line, '\n')) {
    dark_mode = line == "Night mode: yes";
  }
  response->set_dark_mode(dark_mode);
}

void ProcessFontSize(stringstream* stream, UiSettingsResponse* response) {
  string line;
  float font_size = 1;
  if (getline(*stream, line, '\n')) {
    sscanf(line.c_str(), "%g", &font_size);
  }
  response->set_font_size(font_size * 100.);
}

void ProcessAdbOutput(const string& output, UiSettingsResponse* response) {
  stringstream stream(output);
  string line;
  while (getline(stream, line, '\n')) {
    if (line == DARK_MODE_DIVIDER) ProcessDarkMode(&stream, response);
    if (line == FONT_SIZE_DIVIDER) ProcessFontSize(&stream, response);
  }
}

} // namespace

void UiSettings::Get(UiSettingsResponse* response) {
  string command =
    "echo " DARK_MODE_DIVIDER "; "
    "cmd uimode night; "
    "echo " FONT_SIZE_DIVIDER "; "
    "settings get system font_scale";

  string output = ExecuteShellCommand(command);
  ProcessAdbOutput(TrimEnd(output), response);
}

void UiSettings::SetDarkMode(bool dark_mode) {
  string command = string("cmd uimode night ") + (dark_mode ? "yes" : "no");
  ExecuteShellCommand(command);
}

void UiSettings::SetFontSize(int32_t font_size) {
  string command = string("settings put system font_scale ") + StringPrintf("%g", font_size / 100.0f);
  ExecuteShellCommand(command);
}

}  // namespace screensharing
