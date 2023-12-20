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

#include <numeric>
#include <regex>
#include <set>
#include <sstream>

#include "agent.h"
#include "flags.h"
#include "shell_command_executor.h"
#include "string_printf.h"

namespace screensharing {

using namespace std;

namespace {

#define DIVIDER_PREFIX "-- "
#define DARK_MODE_DIVIDER "-- Dark Mode --"
#define LIST_PACKAGES_DIVIDER "-- List Packages --"
#define ACCESSIBILITY_SERVICES_DIVIDER "-- Accessibility Services --"
#define ACCESSIBILITY_BUTTON_TARGETS_DIVIDER "-- Accessibility Button Targets --"
#define FONT_SIZE_DIVIDER "-- Font Size --"
#define DENSITY_DIVIDER "-- Density --"
#define APP_LANGUAGE_DIVIDER "-- App Language --"

#define ENABLED_ACCESSIBILITY_SERVICES "enabled_accessibility_services"
#define ACCESSIBILITY_BUTTON_TARGETS "accessibility_button_targets"
#define TALKBACK_PACKAGE_NAME "com.google.android.marvin.talkback"
#define TALK_BACK_SERVICE_CLASS "com.google.android.marvin.talkback.TalkBackService"
#define TALK_BACK_SERVICE_NAME TALKBACK_PACKAGE_NAME "/" TALK_BACK_SERVICE_CLASS
#define SELECT_TO_SPEAK_SERVICE_CLASS "com.google.android.accessibility.selecttospeak.SelectToSpeakService"
#define SELECT_TO_SPEAK_SERVICE_NAME TALKBACK_PACKAGE_NAME "/" SELECT_TO_SPEAK_SERVICE_CLASS
#define PHYSICAL_DENSITY_PATTERN "Physical density: %d"
#define OVERRIDE_DENSITY_PATTERN "Override density: %d"

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

void ProcessListPackages(stringstream* stream, UiSettingsResponse* response) {
  string line;
  string talkBackServiceLine = string("package:" TALKBACK_PACKAGE_NAME);
  bool talkback_installed = false;
  int line_start_position = stream->tellg();
  while (getline(*stream, line, '\n')) {
    if (line.rfind(DIVIDER_PREFIX, 0) == 0) { // line.startsWith(DIVIDER_PREFIX)
      stream->seekg(line_start_position); // Go back to start of line
      break;
    }
    line_start_position = stream->tellg();
    talkback_installed = talkback_installed || (line == talkBackServiceLine);
  }
  response->set_talkback_installed(talkback_installed);
}

void GetAccessibilityServices(string accessibility_line, set<string>* services) {
  if (accessibility_line == "null") {
    return;
  }
  stringstream ss(accessibility_line);
  string service;
  while (getline(ss, service, ':')) {
    services->insert(service);
  }
}

void ProcessAccessibilityServices(stringstream* stream, set<string>* services) {
  string line;
  if (getline(*stream, line, '\n')) {
    GetAccessibilityServices(line, services);
  }
}

void ProcessFontSize(stringstream* stream, UiSettingsResponse* response) {
  string line;
  float font_size = 1;
  if (getline(*stream, line, '\n')) {
    sscanf(line.c_str(), "%g", &font_size);
  }
  response->set_font_size(font_size * 100.);
}

void ReadDensity(stringstream* stream, const char* pattern, int* density) {
  string line;
  *density = 0;
  int line_start_position = stream->tellg();
  if (getline(*stream, line, '\n')) {
    if (line.rfind(DIVIDER_PREFIX, 0) == 0) { // line.startsWith(DIVIDER_PREFIX)
      stream->seekg(line_start_position); // Go back to start of line
      return;
    }
    sscanf(line.c_str(), pattern, density);
  }
}

void ProcessDensity(stringstream* stream, UiSettingsResponse* response) {
  int physical_density, override_density;
  ReadDensity(stream, PHYSICAL_DENSITY_PATTERN, &physical_density);
  ReadDensity(stream, OVERRIDE_DENSITY_PATTERN, &override_density);
  if (physical_density == 0) {
    physical_density = 160;
  }
  if (override_density == 0) {
    override_density = physical_density;
  }
  response->set_density(override_density);
}

// Example: "Locales for com.example.process for user 0 are [es-CL,es]"
bool ParseAppLanguageLine(const string& line, string* application_id, string* locales) {
  regex app_locale_pattern("Locales for (.+) for user \\d+ are \\[(.*)\\]");
  smatch match;
  try {
    if (!regex_match(line, match, app_locale_pattern) || match.size() != 3) {
      return false;
    }
  } catch (const std::regex_error& e) {
    return false;
  }
  *application_id = match[1];
  *locales = match[2];
  return true;
}

void ProcessAppLanguage(stringstream* stream, UiSettingsResponse* response) {
  string line;
  string locale;
  string application_id;
  int line_start_position = stream->tellg();
  if (getline(*stream, line, '\n')) {
    if (line.rfind(DIVIDER_PREFIX, 0) == 0) { // line.startsWith(DIVIDER_PREFIX)
      stream->seekg(line_start_position); // Go back to start of line
      return;
    }
    string locales;
    if (ParseAppLanguageLine(line, &application_id, &locales)) {
      stringstream ss(locales);
      getline(ss, locale, ',');  // Read the first locale, ignore the rest
      response->add_app_locale(application_id, locale);
    }
  }
}

void ProcessAccessibility(const set<string>& enabled, const set<string>& buttons, UiSettingsResponse* response) {
  bool talkback_on = enabled.find(string(TALK_BACK_SERVICE_NAME)) != enabled.end();
  bool select_to_speak_on =
    enabled.find(string(SELECT_TO_SPEAK_SERVICE_NAME)) != enabled.end() &&
    buttons.find(string(SELECT_TO_SPEAK_SERVICE_NAME)) != buttons.end();

  response->set_talkback_on(talkback_on);
  response->set_select_to_speak_on(select_to_speak_on);
}

void ProcessAdbOutput(const string& output, UiSettingsResponse* response) {
  stringstream stream(output);
  string line;
  set<string> enabled;
  set<string> buttons;
  while (getline(stream, line, '\n')) {
    if (line == DARK_MODE_DIVIDER) ProcessDarkMode(&stream, response);
    if (line == LIST_PACKAGES_DIVIDER) ProcessListPackages(&stream, response);
    if (line == ACCESSIBILITY_SERVICES_DIVIDER) ProcessAccessibilityServices(&stream, &enabled);
    if (line == ACCESSIBILITY_BUTTON_TARGETS_DIVIDER) ProcessAccessibilityServices(&stream, &buttons);
    if (line == FONT_SIZE_DIVIDER) ProcessFontSize(&stream, response);
    if (line == DENSITY_DIVIDER) ProcessDensity(&stream, response);
    if (line == APP_LANGUAGE_DIVIDER) ProcessAppLanguage(&stream, response);
  }
  ProcessAccessibility(enabled, buttons, response);
}

string CombineServices(string serviceA, string serviceB) {
  if (serviceA.empty()) {
    return serviceB;
  }
  return serviceA + ":" + serviceB;
}

void ChangeSecureSetting(string settingsName, string serviceName, bool on) {
  string output = ExecuteShellCommand("settings get secure " + settingsName);
  set<string> services;
  GetAccessibilityServices(TrimEnd(output), &services);

  if (on) {
    services.insert(serviceName);
  } else {
    services.erase(serviceName);
  }
  if (services.empty()) {
    ExecuteShellCommand("settings delete secure " + settingsName);
  } else {
    string result = accumulate(services.begin(), services.end(), string(), CombineServices);
    ExecuteShellCommand("settings put secure " + settingsName + " " + result);
  }
}

} // namespace

UiSettings::UiSettings()
  : initial_settings_(-1),
    last_settings_(-1) {
}

void UiSettings::Get(const UiSettingsRequest& request, UiSettingsResponse* response) {
  string command =
    "echo " DARK_MODE_DIVIDER "; "
    "cmd uimode night; "
    "echo " LIST_PACKAGES_DIVIDER "; "
    "pm list packages; "
    "echo " ACCESSIBILITY_SERVICES_DIVIDER "; "
    "settings get secure " ENABLED_ACCESSIBILITY_SERVICES "; "
    "echo " ACCESSIBILITY_BUTTON_TARGETS_DIVIDER "; "
    "settings get secure " ACCESSIBILITY_BUTTON_TARGETS "; "
    "echo " FONT_SIZE_DIVIDER "; "
    "settings get system font_scale; "
    "echo " DENSITY_DIVIDER "; "
    "wm density; ";

  for (auto it = begin(request.application_ids()); it != end(request.application_ids()); it++) {
    command += "echo " APP_LANGUAGE_DIVIDER "; ";
    command += "cmd locale get-app-locales ";
    command += *it;
    command += "; ";
  }

  string output = ExecuteShellCommand(command.c_str());
  ProcessAdbOutput(TrimEnd(output), response);
  StoreInitialSettings(*response);
}

void UiSettings::StoreInitialSettings(const UiSettingsResponse& response) {
  if (!initial_settings_recorded_) {
    initial_settings_recorded_ = true;
    response.copy(&initial_settings_);
    response.copy(&last_settings_);
  }
  // Add any application_ids not seen yet if applicable:
  for (map<string, string>::const_iterator it = response.app_locales().begin(); it != response.app_locales().end(); it++) {
    if (initial_settings_.app_locales().count(it->first) == 0) {
      initial_settings_.add_app_locale(it->first, it->second);
      last_settings_.add_app_locale(it->first, it->second);
    }
  }
}

void UiSettings::SetDarkMode(bool dark_mode) {
  string command = string("cmd uimode night ") + (dark_mode ? "yes" : "no");
  ExecuteShellCommand(command);
  last_settings_.set_dark_mode(dark_mode);
}

void UiSettings::SetAppLanguage(const string& application_id, const string& locale) {
  string command = StringPrintf("cmd locale set-app-locales %s --locales %s", application_id.c_str(), locale.c_str());
  ExecuteShellCommand(command);
  last_settings_.add_app_locale(application_id, locale);
}

void UiSettings::SetTalkBack(bool on) {
  ChangeSecureSetting(ENABLED_ACCESSIBILITY_SERVICES, TALK_BACK_SERVICE_NAME, on);
  last_settings_.set_talkback_on(on);
}

void UiSettings::SetSelectToSpeak(bool on) {
  ChangeSecureSetting(ENABLED_ACCESSIBILITY_SERVICES, SELECT_TO_SPEAK_SERVICE_NAME, on);
  ChangeSecureSetting(ACCESSIBILITY_BUTTON_TARGETS, SELECT_TO_SPEAK_SERVICE_NAME, on);
  last_settings_.set_select_to_speak_on(on);
}

void UiSettings::SetFontSize(int32_t font_size) {
  string command = string("settings put system font_scale ") + StringPrintf("%g", font_size / 100.0f);
  ExecuteShellCommand(command);
  last_settings_.set_font_size(font_size);
}

void UiSettings::SetScreenDensity(int32_t density) {
  string command = StringPrintf("wm density %d", density);
  ExecuteShellCommand(command);
  last_settings_.set_density(density);
}

// Reset all changed settings to the initial state.
// If the user overrides any setting on the device the original state is ignored.
void UiSettings::Reset() {
  if (!initial_settings_recorded_ || (Agent::flags() & AUTO_RESET_UI_SETTINGS) == 0) {
    return;
  }

  vector<string> application_ids;
  for (map<string, string>::const_iterator it = initial_settings_.app_locales().begin(); it != initial_settings_.app_locales().end(); it++) {
    application_ids.push_back(it->first);
  }

  UiSettingsRequest request(-1, application_ids);
  UiSettingsResponse current_settings(-1);
  Get(request, &current_settings);

  if (current_settings.dark_mode() != initial_settings_.dark_mode() &&
      current_settings.dark_mode() == last_settings_.dark_mode()) {
    SetDarkMode(initial_settings_.dark_mode());
  }
  const map<string, string>& initial_locales = initial_settings_.app_locales();
  const map<string, string>& last_locales = last_settings_.app_locales();
  for (map<string, string>::const_iterator it = current_settings.app_locales().begin(); it != current_settings.app_locales().end(); it++) {
    if (initial_locales.count(it->first) > 0 && it->second != initial_locales.at(it->first) &&
        last_locales.count(it->first) > 0 && it->second == last_locales.at(it->first)) {
      SetAppLanguage(it->first, initial_locales.at(it->first));
    }
  }
  if (current_settings.talkback_on() != initial_settings_.talkback_on() &&
      current_settings.talkback_on() == last_settings_.talkback_on()) {
    SetTalkBack(initial_settings_.talkback_on());
  }
  if (current_settings.select_to_speak_on() != initial_settings_.select_to_speak_on() &&
      current_settings.select_to_speak_on() == last_settings_.select_to_speak_on()) {
    SetSelectToSpeak(initial_settings_.select_to_speak_on());
  }
  if (current_settings.font_size() != initial_settings_.font_size() &&
      current_settings.font_size() == last_settings_.font_size()) {
    SetFontSize(initial_settings_.font_size());
  }
  if (current_settings.density() != initial_settings_.density() &&
      current_settings.density() == last_settings_.density()) {
    SetScreenDensity(initial_settings_.density());
  }
}

}  // namespace screensharing
