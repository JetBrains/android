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
#define GESTURES_DIVIDER "-- Gestures --"
#define LIST_PACKAGES_DIVIDER "-- List Packages --"
#define ACCESSIBILITY_SERVICES_DIVIDER "-- Accessibility Services --"
#define ACCESSIBILITY_BUTTON_TARGETS_DIVIDER "-- Accessibility Button Targets --"
#define FONT_SIZE_DIVIDER "-- Font Size --"
#define DENSITY_DIVIDER "-- Density --"
#define FOREGROUND_APPLICATION_DIVIDER "-- Foreground Application --"
#define APP_LANGUAGE_DIVIDER "-- App Language --"

#define GESTURES_OVERLAY "com.android.internal.systemui.navbar.gestural"
#define THREE_BUTTON_OVERLAY "com.android.internal.systemui.navbar.threebutton"
#define ENABLED_ACCESSIBILITY_SERVICES "enabled_accessibility_services"
#define ACCESSIBILITY_BUTTON_TARGETS "accessibility_button_targets"
#define TALKBACK_PACKAGE_NAME "com.google.android.marvin.talkback"
#define TALK_BACK_SERVICE_CLASS "com.google.android.marvin.talkback.TalkBackService"
#define TALK_BACK_SERVICE_NAME TALKBACK_PACKAGE_NAME "/" TALK_BACK_SERVICE_CLASS
#define SELECT_TO_SPEAK_SERVICE_CLASS "com.google.android.accessibility.selecttospeak.SelectToSpeakService"
#define SELECT_TO_SPEAK_SERVICE_NAME TALKBACK_PACKAGE_NAME "/" SELECT_TO_SPEAK_SERVICE_CLASS
#define PHYSICAL_DENSITY_PATTERN "Physical density: %d"
#define OVERRIDE_DENSITY_PATTERN "Override density: %d"

struct CommandContext {
    set<string> enabled;
    set<string> buttons;
    string foreground_application_id;
};

string TrimEnd(string value) {
  while (!value.empty() && value.back() <= ' ') {
    value.erase(value.size() - 1);
  }
  return value;
}

void ProcessDarkMode(stringstream* stream, UiSettingsState* state) {
  string line;
  bool dark_mode = false;
  if (getline(*stream, line, '\n')) {
    dark_mode = line == "Night mode: yes";
  }
  state->set_dark_mode(dark_mode);
}

void ProcessGestureNavigation(stringstream* stream, UiSettingsState* state) {
  string line;
  bool gesture_overlay_installed = false;
  bool gesture_navigation = false;
  int line_start_position = stream->tellg();
  if (getline(*stream, line, '\n')) {
    if (line.rfind(DIVIDER_PREFIX, 0) == 0) { // line.startsWith(DIVIDER_PREFIX)
      stream->seekg(line_start_position); // Go back to start of line
    } else {
      gesture_overlay_installed = true;
      gesture_navigation = line == "[x] " GESTURES_OVERLAY;
    }
  }
  state->set_gesture_overlay_installed(gesture_overlay_installed);
  state->set_gesture_navigation(gesture_navigation);
}

void ProcessListPackages(stringstream* stream, UiSettingsState* state) {
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
  state->set_talkback_installed(talkback_installed);
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

void ProcessFontSize(stringstream* stream, UiSettingsState* state) {
  string line;
  float font_size = 1;
  if (getline(*stream, line, '\n')) {
    sscanf(line.c_str(), "%g", &font_size);
  }
  state->set_font_size(font_size * 100.);
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

void ProcessDensity(stringstream* stream, UiSettingsState* state) {
  int physical_density, override_density;
  ReadDensity(stream, PHYSICAL_DENSITY_PATTERN, &physical_density);
  ReadDensity(stream, OVERRIDE_DENSITY_PATTERN, &override_density);
  if (physical_density == 0) {
    physical_density = 160;
  }
  if (override_density == 0) {
    override_density = physical_density;
  }
  state->set_density(override_density);
}

// Example: "    Proc # 0: fg     T/A/TOP  LCMNFU  t: 0 17132:com.example.process1/u0a405 (top-activity)"
bool ParseForegroundProcessLine(const string& line, string* foreground_application_id) {
  regex pattern("\\d*:(\\S*)/\\S* \\(top-activity\\)");
  smatch match;
  try {
    if (!regex_search(line, match, pattern) || match.size() != 2) {
      return false;
    }
  } catch (const std::regex_error& e) {
    return false;
  }
  *foreground_application_id = match[1];
  return true;
}

void ProcessForegroundProcess(stringstream* stream, CommandContext* context) {
  string line;
  string locale;
  string application_id;
  int line_start_position = stream->tellg();
  if (getline(*stream, line, '\n')) {

    if (line.rfind(DIVIDER_PREFIX, 0) == 0) { // line.startsWith(DIVIDER_PREFIX)
      stream->seekg(line_start_position); // Go back to start of line
      return;
    }
    string foreground_application_id;
    if (ParseForegroundProcessLine(line, &foreground_application_id)) {
      context->foreground_application_id = foreground_application_id;
    }
  }
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

void ProcessAppLanguage(stringstream* stream, UiSettingsState* state) {
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
      state->add_app_locale(application_id, locale);
    }
  }
}

void ProcessAccessibility(const CommandContext& context, UiSettingsState* state) {
  bool talkback_on = context.enabled.find(string(TALK_BACK_SERVICE_NAME)) != context.enabled.end();
  bool select_to_speak_on =
    context.enabled.find(string(SELECT_TO_SPEAK_SERVICE_NAME)) != context.enabled.end() &&
    context.buttons.find(string(SELECT_TO_SPEAK_SERVICE_NAME)) != context.buttons.end();

  state->set_talkback_on(talkback_on);
  state->set_select_to_speak_on(select_to_speak_on);
}

void ProcessAdbOutput(const string& output, UiSettingsState* state, CommandContext* context) {
  stringstream stream(output);
  string line;
  while (getline(stream, line, '\n')) {
    if (line == DARK_MODE_DIVIDER) ProcessDarkMode(&stream, state);
    if (line == GESTURES_DIVIDER) ProcessGestureNavigation(&stream, state);
    if (line == LIST_PACKAGES_DIVIDER) ProcessListPackages(&stream, state);
    if (line == ACCESSIBILITY_SERVICES_DIVIDER) ProcessAccessibilityServices(&stream, &context->enabled);
    if (line == ACCESSIBILITY_BUTTON_TARGETS_DIVIDER) ProcessAccessibilityServices(&stream, &context->buttons);
    if (line == FONT_SIZE_DIVIDER) ProcessFontSize(&stream, state);
    if (line == DENSITY_DIVIDER) ProcessDensity(&stream, state);
    if (line == FOREGROUND_APPLICATION_DIVIDER) ProcessForegroundProcess(&stream, context);
    if (line == APP_LANGUAGE_DIVIDER) ProcessAppLanguage(&stream, state);
  }
}

void GetApplicationLocales(const vector<string>& application_ids, UiSettingsState* state) {
  string command;
  for (auto it = application_ids.begin(); it != application_ids.end(); it++) {
    command += "echo " APP_LANGUAGE_DIVIDER "; ";
    command += "cmd locale get-app-locales ";
    command += *it;
    command += "; ";
  }
  string output = ExecuteShellCommand(command.c_str());
  ProcessAdbOutput(TrimEnd(output), state, nullptr);
}

bool IsFontSizeSettable(int32_t font_size) {
  string command = StringPrintf("settings put system font_scale %g 2>&1 >/dev/null", font_size / 100.0f);
  string error = ExecuteShellCommand(command);
  return error.empty();
}

bool IsScreenDensitySettable(int32_t density) {
  string command = StringPrintf("wm density %d 2>&1 >/dev/null", density);
  string error = ExecuteShellCommand(command);
  return error.empty();
}

string CreateSetDarkModeCommand(bool dark_mode) {
  return string("cmd uimode night ") + (dark_mode ? "yes" : "no") + ";\n";
}

string CreateSetGestureNavigationCommand(bool gesture_navigation) {
  auto operation = gesture_navigation ? "enable" : "disable";
  auto opposite = !gesture_navigation ? "enable" : "disable";
  return StringPrintf("cmd overlay %s " GESTURES_OVERLAY "; cmd overlay %s " THREE_BUTTON_OVERLAY ";\n", operation, opposite);
}

string CreateSetAppLanguageCommand(const string& application_id, const string& locale) {
  return StringPrintf("cmd locale set-app-locales %s --locales %s;\n", application_id.c_str(), locale.c_str());
}

void GetSecureSettings(CommandContext* context) {
  string command =
    "echo " ACCESSIBILITY_SERVICES_DIVIDER "; "
    "settings get secure " ENABLED_ACCESSIBILITY_SERVICES "; "
    "echo " ACCESSIBILITY_BUTTON_TARGETS_DIVIDER "; "
    "settings get secure " ACCESSIBILITY_BUTTON_TARGETS "; ";
  string output = ExecuteShellCommand(command.c_str());
  UiSettingsState ignored;
  ProcessAdbOutput(TrimEnd(output), &ignored, context);
}

string CombineServices(string serviceA, string serviceB) {
  if (serviceA.empty()) {
    return serviceB;
  }
  return serviceA + ":" + serviceB;
}

string CreateSecureSettingChangeCommand(bool on, string settingsName, string serviceName, set<string>* services) {
  if (on) {
    services->insert(serviceName);
  } else {
    services->erase(serviceName);
  }
  if (services->empty()) {
    return "settings delete secure " + settingsName + ";\n";
  } else {
    string result = accumulate(services->begin(), services->end(), string(), CombineServices);
    return "settings put secure " + settingsName + " " + result + ";\n";
  }
}

string CreateSetTalkBackCommand(bool on, CommandContext* context) {
  return CreateSecureSettingChangeCommand(on, ENABLED_ACCESSIBILITY_SERVICES, TALK_BACK_SERVICE_NAME, &context->enabled);
}

string CreateSetSelectToSpeakCommand(bool on, CommandContext* context) {
  return CreateSecureSettingChangeCommand(on, ENABLED_ACCESSIBILITY_SERVICES, SELECT_TO_SPEAK_SERVICE_NAME, &context->enabled)
      +  CreateSecureSettingChangeCommand(on, ACCESSIBILITY_BUTTON_TARGETS, SELECT_TO_SPEAK_SERVICE_NAME, &context->buttons);
}

string CreateSetFontSizeCommand(int32_t font_size) {
  return string("settings put system font_scale ") + StringPrintf("%g", font_size / 100.0f) + ";\n";
}

string CreateSetScreenDensityCommand(int32_t density) {
  return StringPrintf("wm density %d;\n", density);
}

void GetSettings(UiSettingsState* state, CommandContext* context) {
  string command =
    "echo " DARK_MODE_DIVIDER "; "
    "cmd uimode night; "
    "echo " GESTURES_DIVIDER "; "
    "cmd overlay list android | grep " GESTURES_OVERLAY "$; "
    "echo " LIST_PACKAGES_DIVIDER "; "
    "pm list packages; "
    "echo " ACCESSIBILITY_SERVICES_DIVIDER "; "
    "settings get secure " ENABLED_ACCESSIBILITY_SERVICES "; "
    "echo " ACCESSIBILITY_BUTTON_TARGETS_DIVIDER "; "
    "settings get secure " ACCESSIBILITY_BUTTON_TARGETS "; "
    "echo " FONT_SIZE_DIVIDER "; "
    "settings get system font_scale; "
    "echo " DENSITY_DIVIDER "; "
    "wm density; "
    "echo " FOREGROUND_APPLICATION_DIVIDER "; "
    "dumpsys activity processes | grep top-activity; ";

  string output = ExecuteShellCommand(command.c_str());
  ProcessAdbOutput(TrimEnd(output), state, context);

  auto foreground_application_id = context->foreground_application_id;
  if (!foreground_application_id.empty()) {
    vector<string> application_ids;
    application_ids.push_back(foreground_application_id);
    GetApplicationLocales(application_ids, state);
  }
  ProcessAccessibility(*context, state);
}

} // namespace

void UiSettings::Get(UiSettingsResponse* response) {
  UiSettingsState state;
  CommandContext context;
  GetSettings(&state, &context);
  StoreInitialSettings(state);
  state.copy(response);
  vector<string> application_ids = state.get_application_ids();
  string foreground_application_id = application_ids.size() == 1 ? application_ids.at(0) : "";
  response->set_foreground_application_id(foreground_application_id);
  response->set_app_locale(state.app_locale_of(foreground_application_id));
  response->set_font_size_settable(IsFontSizeSettable(state.font_size()));
  response->set_density_settable(IsScreenDensitySettable(state.density()));
}

void UiSettings::StoreInitialSettings(const UiSettingsState& state) {
  if (!initial_settings_recorded_) {
    initial_settings_recorded_ = true;
    state.copy(&initial_settings_);
    state.copy(&last_settings_);
  }
  // Add any foreground_application_id and their app_locales not seen yet if applicable:
  state.add_unseen_app_locales(&initial_settings_);
  state.add_unseen_app_locales(&last_settings_);
}

void UiSettings::SetDarkMode(bool dark_mode) {
  ExecuteShellCommand(CreateSetDarkModeCommand(dark_mode));
  last_settings_.set_dark_mode(dark_mode);
}

void UiSettings::SetGestureNavigation(bool gesture_navigation) {
  ExecuteShellCommand(CreateSetGestureNavigationCommand(gesture_navigation));
  last_settings_.set_gesture_navigation(gesture_navigation);
}

void UiSettings::SetAppLanguage(const string& application_id, const string& locale) {
  ExecuteShellCommand(CreateSetAppLanguageCommand(application_id, locale));
  last_settings_.add_app_locale(application_id, locale);
}

void UiSettings::SetTalkBack(bool on) {
  CommandContext context;
  GetSecureSettings(&context);
  ExecuteShellCommand(CreateSetTalkBackCommand(on, &context));
  last_settings_.set_talkback_on(on);
}

void UiSettings::SetSelectToSpeak(bool on) {
  CommandContext context;
  GetSecureSettings(&context);
  ExecuteShellCommand(CreateSetSelectToSpeakCommand(on, &context));
  last_settings_.set_select_to_speak_on(on);
}

void UiSettings::SetFontSize(int32_t font_size) {
  ExecuteShellCommand(CreateSetFontSizeCommand(font_size));
  last_settings_.set_font_size(font_size);
}

void UiSettings::SetScreenDensity(int32_t density) {
  ExecuteShellCommand(CreateSetScreenDensityCommand(density));
  last_settings_.set_density(density);
}

const string UiSettings::CreateResetCommand() {
  if (!initial_settings_recorded_ || (Agent::flags() & AUTO_RESET_UI_SETTINGS) == 0) {
    return "";
  }

  UiSettingsState current_settings;
  CommandContext context;
  GetSettings(&current_settings, &context);
  vector<string> application_ids = initial_settings_.get_application_ids();
  GetApplicationLocales(application_ids, &current_settings);

  string command;
  if (current_settings.dark_mode() != initial_settings_.dark_mode() &&
      current_settings.dark_mode() == last_settings_.dark_mode()) {
    command += CreateSetDarkModeCommand(initial_settings_.dark_mode());
  }
  if (current_settings.gesture_navigation() != initial_settings_.gesture_navigation() &&
      current_settings.gesture_navigation() == last_settings_.gesture_navigation()) {
    command += CreateSetGestureNavigationCommand(initial_settings_.gesture_navigation());
  }
  for (auto it = application_ids.begin(); it != application_ids.end(); it++) {
    if (current_settings.app_locale_of(*it) != initial_settings_.app_locale_of(*it) &&
        current_settings.app_locale_of(*it) == last_settings_.app_locale_of(*it)) {
      command += CreateSetAppLanguageCommand(*it, initial_settings_.app_locale_of(*it));
    }
  }
  if (current_settings.talkback_on() != initial_settings_.talkback_on() &&
      current_settings.talkback_on() == last_settings_.talkback_on()) {
    command += CreateSetTalkBackCommand(initial_settings_.talkback_on(), &context);
  }
  if (current_settings.select_to_speak_on() != initial_settings_.select_to_speak_on() &&
      current_settings.select_to_speak_on() == last_settings_.select_to_speak_on()) {
    command += CreateSetSelectToSpeakCommand(initial_settings_.select_to_speak_on(), &context);
  }
  if (current_settings.font_size() != initial_settings_.font_size() &&
      current_settings.font_size() == last_settings_.font_size()) {
    command += CreateSetFontSizeCommand(initial_settings_.font_size());
  }
  if (current_settings.density() != initial_settings_.density() &&
      current_settings.density() == last_settings_.density()) {
    command += CreateSetScreenDensityCommand(initial_settings_.density());
  }
  return command;
}

// Reset all changed settings to the initial state.
// If the user overrides any setting on the device the original state is ignored.
void UiSettings::Reset() {
  string command = CreateResetCommand();
  if (!command.empty()) {
    ExecuteShellCommand(command);
    initial_settings_.copy(&last_settings_);
  }
}

}  // namespace screensharing
