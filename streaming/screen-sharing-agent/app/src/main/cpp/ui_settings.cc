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

#include <cmath>
#include <numeric>
#include <regex>
#include <set>

#include "agent.h"
#include "flags.h"
#include "shell_command_executor.h"
#include "string_printf.h"
#include "token_iterator.h"

namespace screensharing {

using namespace std;

namespace {

#define DIVIDER_PREFIX "-- "
#define DARK_MODE_DIVIDER "-- Dark Mode --"
#define GESTURES_DIVIDER "-- Gestures --"
#define OEM_GESTURES_DIVIDER "-- OEM Gestures --"
#define LIST_PACKAGES_DIVIDER "-- List Packages --"
#define ACCESSIBILITY_SERVICES_DIVIDER "-- Accessibility Services --"
#define ACCESSIBILITY_BUTTON_TARGETS_DIVIDER "-- Accessibility Button Targets --"
#define FONT_SCALE_DIVIDER "-- Font Scale --"
#define DENSITY_DIVIDER "-- Density --"
#define DEBUG_LAYOUT_DIVIDER "-- Debug Layout --"
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

#define GOOGLE "Google"
#define MOTOROLA "motorola"
#define ONE_PLUS "OnePlus"
#define OPPO "OPPO"
#define SAMSUNG "samsung"
#define VIVO "vivo"
#define XIAOMI "Xiaomi"

#define SYSPROPS_TRANSACTION 1599295570 // from frameworks/base/core/java/android/os/IBinder.java

struct CommandContext {
    set<string> enabled;
    set<string> buttons;
    string foreground_application_id;
    bool secure_settings_retrieved = false;
};

string TrimEnd(string value) {
  while (!value.empty() && value.back() <= ' ') {
    value.erase(value.size() - 1);
  }
  return value;
}

bool StartsWithDividerPrefix(const string& value) {
  return strncmp(value.c_str(), DIVIDER_PREFIX, 3) == 0;
}

void ProcessDarkMode(TokenIterator* it, UiSettingsState* state) {
  bool dark_mode = it->has_next() && strcmp(it->next(), "Night mode: yes") == 0;
  state->set_dark_mode(dark_mode);
}

void ProcessGestureNavigation(TokenIterator* it, UiSettingsState* state) {
  bool gesture_overlay_installed = false;
  bool gesture_navigation = false;
  if (it->has_next()) {
    string line = it->next();
    if (StartsWithDividerPrefix(line)) {
      it->prev(); // Go back to start of line
    } else {
      gesture_overlay_installed = true;
      gesture_navigation = line == "[x] " GESTURES_OVERLAY;
    }
  }
  state->set_gesture_overlay_installed(gesture_overlay_installed);
  state->set_gesture_navigation(gesture_navigation);
}

void ProcessOemGestureNavigation(TokenIterator* it, UiSettingsState* state) {
  bool gesture_overlay_installed = false;
  bool gesture_navigation = false;
  if (it->has_next()) {
    string line = it->next();
    if (StartsWithDividerPrefix(line)) {
      it->prev(); // Go back to start of line
    } else {
      int value = 0;
      sscanf(line.c_str(), "%d", &value);
      gesture_overlay_installed = true;
      gesture_navigation = value > 0;
    }
  }
  state->set_gesture_overlay_installed(gesture_overlay_installed);
  state->set_gesture_navigation(gesture_navigation);
}

void ProcessListPackages(TokenIterator* it, UiSettingsState* state) {
  string talkBackServiceLine = string("package:" TALKBACK_PACKAGE_NAME);
  bool talkback_installed = false;
  while (it->has_next()) {
    string line = it->next();
    if (StartsWithDividerPrefix(line)) {
      it->prev(); // Go back to start of line
      break;
    }
    talkback_installed = talkback_installed || (line == talkBackServiceLine);
  }
  state->set_talkback_installed(talkback_installed);
}

void GetAccessibilityServices(string accessibility_line, set<string>* services) {
  if (accessibility_line == "null") {
    return;
  }
  TokenIterator it(accessibility_line, ':');
  string service;
  while (it.has_next()) {
    services->insert(it.next());
  }
}

void ProcessAccessibilityServices(TokenIterator* it, set<string>* services) {
  if (it->has_next()) {
    string line = it->next();
    GetAccessibilityServices(line, services);
  }
}

void ProcessFontScale(TokenIterator* it, UiSettingsState* state) {
  float font_scale = 1;
  sscanf(it->has_next() ? it->next() : "1.0", "%g", &font_scale);
  state->set_font_scale(lround(font_scale * 100.));
}

void ReadDensity(TokenIterator* it, const char* pattern, int* density) {
  *density = 0;
  if (it->has_next()) {
    string line = it->next();
    if (StartsWithDividerPrefix(line)) {
      it->prev(); // Go back to start of line
    } else {
      sscanf(line.c_str(), pattern, density);
    }
  }
}

void ProcessDensity(TokenIterator* it, UiSettingsState* state) {
  int physical_density, override_density;
  ReadDensity(it, PHYSICAL_DENSITY_PATTERN, &physical_density);
  ReadDensity(it, OVERRIDE_DENSITY_PATTERN, &override_density);
  if (physical_density == 0) {
    physical_density = 160;
  }
  if (override_density == 0) {
    override_density = physical_density;
  }
  state->set_density(override_density);
}

void ProcessDebugLayout(TokenIterator* it, UiSettingsState* state) {
  bool debug_layout = false;
  if (it->has_next()) {
    string line = it->next();
    if (StartsWithDividerPrefix(line)) {
      it->prev(); // Go back to start of line
    } else {
      debug_layout = TrimEnd(line) == "true";
    }
  }
  state->set_debug_layout(debug_layout);
}

// Example: "  mFocusedApp=ActivityRecord{64d5519 u0 com.example.app/com.example.app.MainActivity t8}"
bool ParseForegroundApplicationLine(const string& line, string* foreground_application_id) {
  regex pattern("mFocusedApp=ActivityRecord.* .* (\\S*)/\\S* ");
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

void ProcessForegroundApplication(TokenIterator* it, CommandContext* context) {
  if (it->has_next()) {
    string line = it->next();
    if (StartsWithDividerPrefix(line)) {
      it->prev(); // Go back to start of line
    } else {
      string foreground_application_id;
      if (ParseForegroundApplicationLine(line, &foreground_application_id)) {
        context->foreground_application_id = foreground_application_id;
      }
    }
  }
}

// Example: "Locales for com.example.app for user 0 are [es-CL,es]"
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

void ProcessAppLanguage(TokenIterator* it, UiSettingsState* state) {
  string application_id;
  if (it->has_next()) {
    string line = it->next();
    if (StartsWithDividerPrefix(line)) {
      it->prev(); // Go back to start of line
      return;
    }
    string locales;
    if (ParseAppLanguageLine(line, &application_id, &locales)) {
      string locale;
      TokenIterator locale_it(locales, ',');
      if (locale_it.has_next()) {
        locale = locale_it.next();
        if (locale == "null") {
          locale = "";
        }
      }
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
  TokenIterator it(output);
  while (it.has_next()) {
    string line = it.next();
    if (line == DARK_MODE_DIVIDER) ProcessDarkMode(&it, state);
    if (line == GESTURES_DIVIDER) ProcessGestureNavigation(&it, state);
    if (line == OEM_GESTURES_DIVIDER) ProcessOemGestureNavigation(&it, state);
    if (line == LIST_PACKAGES_DIVIDER) ProcessListPackages(&it, state);
    if (line == ACCESSIBILITY_SERVICES_DIVIDER) ProcessAccessibilityServices(&it, &context->enabled);
    if (line == ACCESSIBILITY_BUTTON_TARGETS_DIVIDER) ProcessAccessibilityServices(&it, &context->buttons);
    if (line == FONT_SCALE_DIVIDER) ProcessFontScale(&it, state);
    if (line == DENSITY_DIVIDER) ProcessDensity(&it, state);
    if (line == DEBUG_LAYOUT_DIVIDER) ProcessDebugLayout(&it, state);
    if (line == FOREGROUND_APPLICATION_DIVIDER) ProcessForegroundApplication(&it, context);
    if (line == APP_LANGUAGE_DIVIDER) ProcessAppLanguage(&it, state);
  }
}

void GetApplicationLocales(const vector<string>& application_ids, UiSettingsState* state) {
  ShellCommand command;
  for (auto it = application_ids.begin(); it != application_ids.end(); it++) {
    command += "echo " APP_LANGUAGE_DIVIDER;
    command += StringPrintf("cmd locale get-app-locales %s", it->c_str());
  }
  string output = ExecuteShellCommand(command);
  ProcessAdbOutput(TrimEnd(output), state, nullptr);
}

bool IsFontScaleSettable(int32_t font_scale) {
  ShellCommand command = StringPrintf("settings put system font_scale %g 2>&1 >/dev/null", font_scale / 100.0f);
  string error = ExecuteShellCommand(command);
  return error.empty();
}

bool IsScreenDensitySettable(int32_t density) {
  ShellCommand command = StringPrintf("wm density %d 2>&1 >/dev/null", density);
  string error = ExecuteShellCommand(command);
  return error.empty();
}

ShellCommand CreateSetDarkModeCommand(bool dark_mode) {
  return StringPrintf("cmd uimode night %s", dark_mode ? "yes" : "no");
}

ShellCommand CreateSetFontScaleCommand(int32_t font_scale) {
  return StringPrintf("settings put system font_scale %g", font_scale / 100.0f);
}

ShellCommand CreateSetScreenDensityCommand(int32_t density) {
  return StringPrintf("wm density %d", density);
}

void GetSecureSettings(CommandContext* context) {
  if (!context->secure_settings_retrieved) {
    ShellCommand command =
      "echo " ACCESSIBILITY_SERVICES_DIVIDER ";\n"
      "settings get secure " ENABLED_ACCESSIBILITY_SERVICES ";\n"
      "echo " ACCESSIBILITY_BUTTON_TARGETS_DIVIDER ";\n"
      "settings get secure " ACCESSIBILITY_BUTTON_TARGETS;
    string output = ExecuteShellCommand(command);
    TokenIterator it(output);
    while (it.has_next()) {
      string line = it.next();
      if (line == ACCESSIBILITY_SERVICES_DIVIDER) ProcessAccessibilityServices(&it, &context->enabled);
      if (line == ACCESSIBILITY_BUTTON_TARGETS_DIVIDER) ProcessAccessibilityServices(&it, &context->buttons);
    }
    context->secure_settings_retrieved = true;
  }
}

string CombineServices(string serviceA, string serviceB) {
  if (serviceA.empty()) {
    return serviceB;
  }
  return serviceA + ":" + serviceB;
}

ShellCommand CreateSecureSettingChangeCommand(bool on, string settingsName, string serviceName, set<string>* services) {
  if (on) {
    services->insert(serviceName);
  } else {
    services->erase(serviceName);
  }
  if (services->empty()) {
    return StringPrintf("settings delete secure %s", settingsName.c_str());
  } else {
    string result = accumulate(services->begin(), services->end(), string(), CombineServices);
    return StringPrintf("settings put secure %s %s", settingsName.c_str(), result.c_str());
  }
}

ShellCommand CreateSetTalkBackCommand(bool on, CommandContext* context) {
  return CreateSecureSettingChangeCommand(on, ENABLED_ACCESSIBILITY_SERVICES, TALK_BACK_SERVICE_NAME, &context->enabled);
}

ShellCommand CreateSetSelectToSpeakCommand(bool on, CommandContext* context) {
  return CreateSecureSettingChangeCommand(on, ENABLED_ACCESSIBILITY_SERVICES, SELECT_TO_SPEAK_SERVICE_NAME, &context->enabled)
      +  CreateSecureSettingChangeCommand(on, ACCESSIBILITY_BUTTON_TARGETS, SELECT_TO_SPEAK_SERVICE_NAME, &context->buttons);
}

ShellCommand CreateDefaultSetGestureNavigationCommand(bool gesture_navigation) {
  auto operation = gesture_navigation ? "enable" : "disable";
  auto opposite = !gesture_navigation ? "enable" : "disable";
  return StringPrintf("cmd overlay %s " GESTURES_OVERLAY "; cmd overlay %s " THREE_BUTTON_OVERLAY, operation, opposite);
}

ShellCommand CreateSetGestureNavigationCommand(bool gesture_navigation) {
  ShellCommand command;
  if (Agent::device_manufacturer() == SAMSUNG) {
    // Samsung devices are sensitive to the global setting: navigation_bar_gesture_while_hidden.
    // Some Samsung devices also need the overlay commands from CreateDefaultSetGestureNavigationCommand.
    // Tested on:
    // - Galaxy Z Fold5 - Android 14 / API 34 [overlay commands optional]
    // - Galaxy A14 - Android 14 / API 34 [overlay commands required]
    // - Galaxy S23 Ultra - Android 14 / API 34 [overlay commands required]
    // - Galaxy Tab S8 Ultra - Android 13 / API 33 [overlay commands required]

    // On these devices changing the value of the global setting: "navigation_bar_gesture_while_hidden" between 0 and 1
    // together with enabling/disabling the overlays would cause the device to change the overlays and update the display
    // properly. If the user is on the device settings "Navigation bar", the settings page would also update correctly.
    command = StringPrintf("settings put global navigation_bar_gesture_while_hidden %d", gesture_navigation ? 1 : 0) +
        CreateDefaultSetGestureNavigationCommand(gesture_navigation);
  }
  else if (Agent::device_manufacturer() == XIAOMI) {
    // Xiaomi devices are sensitive to the global setting: force_fsg_nav_bar. Tested on:
    // - Xiaomi Redmi Note 13 Pro+ - Android 15 / API 34

    // On these devices changing the value of the global setting: "force_fsg_nav_bar" between 0 and 1 would cause the
    // device to change the overlays and update the display properly. If the user is on the device settings "System navigation",
    // the settings page would also update correctly.
    // The overlays settings did not seem to matter.
    command = StringPrintf("settings put global force_fsg_nav_bar %d", gesture_navigation ? 1 : 0);
  }
  else if (Agent::device_manufacturer() == ONE_PLUS || Agent::device_manufacturer() == OPPO) {
    // These devices are sensitive to the secure setting: hide_navigationbar_enable. Tested on:
    // - OnePlus 12 with Android 14 / API 34
    // - OnePlus 8T with Android 14 / API 34
    // - Oppo Reno2 (PCKM00) with Android 11 / API 30

    // On these devices changing the value of the secure setting: "hide_navigationbar_enable" between 0 and 3 would
    // cause the device to change the overlays and update the display properly. For the OnePlus devices: if the user is
    // on the device settings for gesture navigation, the settings page would also update correctly.
    // The overlays settings did not seem to matter.
    command = StringPrintf("settings put secure hide_navigationbar_enable %d", gesture_navigation ? 3 : 0);
  }
  else if (Agent::device_manufacturer() == VIVO) {
    // These devices are sensitive to the secure setting: navigation_gesture_on. Tested on:
    // - Vivo X 90 with Android 14 / API 34

    // On thia devices changing the value of the secure setting: "navigation_gesture_on" between 0 and 2 would
    // cause the device to change the overlays and update the display properly.
    command = StringPrintf("settings put secure navigation_gesture_on %d;\n", gesture_navigation ? 2 : 0);
  }
  else {
    command = CreateDefaultSetGestureNavigationCommand(gesture_navigation);
  }
  return command;
}

ShellCommand CreateSetDebugLayoutCommand(bool debug_layout) {
  auto operation = debug_layout ? "true" : "false";
  return StringPrintf("setprop debug.layout %s; service call activity %d", operation, SYSPROPS_TRANSACTION);
}

ShellCommand CreateSetAppLanguageCommand(const string& application_id, const string& locale) {
  return StringPrintf("cmd locale set-app-locales %s --locales %s", application_id.c_str(), locale.c_str());
}

void GetSettings(UiSettingsState* state, CommandContext* context) {
  ShellCommand command =
    "echo " DARK_MODE_DIVIDER ";\n"
    "cmd uimode night;\n"
    "echo " LIST_PACKAGES_DIVIDER ";\n"
    "pm list packages | grep package:" TALKBACK_PACKAGE_NAME "$;\n"
    "echo " ACCESSIBILITY_SERVICES_DIVIDER ";\n"
    "settings get secure " ENABLED_ACCESSIBILITY_SERVICES ";\n"
    "echo " ACCESSIBILITY_BUTTON_TARGETS_DIVIDER ";\n"
    "settings get secure " ACCESSIBILITY_BUTTON_TARGETS ";\n"
    "echo " FONT_SCALE_DIVIDER ";\n"
    "settings get system font_scale;\n"
    "echo " DENSITY_DIVIDER ";\n"
    "wm density;\n"
    "echo " DEBUG_LAYOUT_DIVIDER ";\n"
    "getprop debug.layout;\n"
    "echo " FOREGROUND_APPLICATION_DIVIDER ";\n"
    "dumpsys activity activities | grep mFocusedApp=ActivityRecord";

  if (Agent::device_manufacturer() == SAMSUNG) {
    command += "echo " OEM_GESTURES_DIVIDER "; settings get global navigation_bar_gesture_while_hidden";
  }
  else if (Agent::device_manufacturer() == XIAOMI) {
    command += "echo " OEM_GESTURES_DIVIDER "; settings get global force_fsg_nav_bar";
  }
  else if (Agent::device_manufacturer() == ONE_PLUS || Agent::device_manufacturer() == OPPO) {
    command += "echo " OEM_GESTURES_DIVIDER "; settings get secure hide_navigationbar_enable";
  }
  else if (Agent::device_manufacturer() == VIVO) {
    command += "echo " OEM_GESTURES_DIVIDER "; settings get secure navigation_gesture_on; ";
  }
  else if (Agent::device_manufacturer() == GOOGLE || Agent::device_manufacturer() == MOTOROLA) {
    command += "echo " GESTURES_DIVIDER "; cmd overlay list android | grep " GESTURES_OVERLAY "$";
  }
  else {
    // This will disable gesture navigation setting on untested devices.
    // Since ProcessOemGestureNavigation will call: state->set_gesture_overlay_installed(false);
    command += "echo " OEM_GESTURES_DIVIDER "";
  }

  string output = ExecuteShellCommand(command);
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
  response->set_font_scale_settable(IsFontScaleSettable(state.font_scale()));
  response->set_density_settable(IsScreenDensitySettable(state.density()));
  response->set_original_values(has_original_values());
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

void UiSettings::SetDarkMode(bool dark_mode, UiSettingsChangeResponse* response) {
  ExecuteShellCommand(CreateSetDarkModeCommand(dark_mode));
  last_settings_.set_dark_mode(dark_mode);
  response->set_original_values(has_original_values());
}

void UiSettings::SetFontScale(int32_t font_scale, UiSettingsChangeResponse* response) {
  ExecuteShellCommand(CreateSetFontScaleCommand(font_scale));
  last_settings_.set_font_scale(font_scale);
  response->set_original_values(has_original_values());
}

void UiSettings::SetScreenDensity(int32_t density, UiSettingsChangeResponse* response) {
  ExecuteShellCommand(CreateSetScreenDensityCommand(density));
  last_settings_.set_density(density);
  response->set_original_values(has_original_values());
}

void UiSettings::SetTalkBack(bool on, UiSettingsChangeResponse* response) {
  CommandContext context;
  GetSecureSettings(&context);
  ExecuteShellCommand(CreateSetTalkBackCommand(on, &context));
  last_settings_.set_talkback_on(on);
  response->set_original_values(has_original_values());
}

void UiSettings::SetSelectToSpeak(bool on, UiSettingsChangeResponse* response) {
  CommandContext context;
  GetSecureSettings(&context);
  ExecuteShellCommand(CreateSetSelectToSpeakCommand(on, &context));
  last_settings_.set_select_to_speak_on(on);
  response->set_original_values(has_original_values());
}

void UiSettings::SetGestureNavigation(bool gesture_navigation, UiSettingsChangeResponse* response) {
  ExecuteShellCommand(CreateSetGestureNavigationCommand(gesture_navigation));
  last_settings_.set_gesture_navigation(gesture_navigation);
  response->set_original_values(has_original_values());
}

void UiSettings::SetDebugLayout(bool debug_layout, UiSettingsChangeResponse* response) {
  ExecuteShellCommand(CreateSetDebugLayoutCommand(debug_layout));
  last_settings_.set_debug_layout(debug_layout);
  response->set_original_values(has_original_values());
}

void UiSettings::SetAppLanguage(const string& application_id, const string& locale, UiSettingsChangeResponse* response) {
  ExecuteShellCommand(CreateSetAppLanguageCommand(application_id, locale));
  last_settings_.add_app_locale(application_id, locale);
  response->set_original_values(has_original_values());
}

const bool UiSettings::has_original_values() {
  return CreateResetCommand().empty();
}

const ShellCommand UiSettings::CreateResetCommand() {
  if (!initial_settings_recorded_) {
    return "";
  }

  vector<string> application_ids = initial_settings_.get_application_ids();
  CommandContext context;

  ShellCommand command;
  if (last_settings_.dark_mode() != initial_settings_.dark_mode()) {
    command += CreateSetDarkModeCommand(initial_settings_.dark_mode());
  }
  if (last_settings_.font_scale() != initial_settings_.font_scale()) {
    command += CreateSetFontScaleCommand(initial_settings_.font_scale());
  }
  if (last_settings_.density() != initial_settings_.density()) {
    command += CreateSetScreenDensityCommand(initial_settings_.density());
  }
  if (last_settings_.talkback_on() != initial_settings_.talkback_on()) {
    GetSecureSettings(&context);
    command += CreateSetTalkBackCommand(initial_settings_.talkback_on(), &context);
  }
  if (last_settings_.select_to_speak_on() != initial_settings_.select_to_speak_on()) {
    GetSecureSettings(&context);
    command += CreateSetSelectToSpeakCommand(initial_settings_.select_to_speak_on(), &context);
  }
  if (last_settings_.gesture_navigation() != initial_settings_.gesture_navigation() && (Agent::flags() & GESTURE_NAVIGATION_UI_SETTINGS) != 0) {
    command += CreateSetGestureNavigationCommand(initial_settings_.gesture_navigation());
  }
  if (last_settings_.debug_layout() != initial_settings_.debug_layout() && (Agent::flags() & DEBUG_LAYOUT_UI_SETTINGS) != 0) {
    command += CreateSetDebugLayoutCommand(initial_settings_.debug_layout());
  }
  for (auto it = application_ids.begin(); it != application_ids.end(); it++) {
    if (last_settings_.app_locale_of(*it) != initial_settings_.app_locale_of(*it)) {
      command += CreateSetAppLanguageCommand(*it, initial_settings_.app_locale_of(*it));
    }
  }
  return command;
}

// Reset all changed settings to the initial state.
// Null response means that the connection to Studio ended.
void UiSettings::Reset(UiSettingsResponse* response) {
  if (response == nullptr && (Agent::flags() & AUTO_RESET_UI_SETTINGS) == 0) {
    // Auto resets are turned off: Do nothing!
    return;
  }
  string command = CreateResetCommand();
  if (!command.empty()) {
    ExecuteShellCommand(command);
    initial_settings_.copy(&last_settings_);
  }
  if (response != nullptr) {
    Get(response);
  }
}

}  // namespace screensharing
