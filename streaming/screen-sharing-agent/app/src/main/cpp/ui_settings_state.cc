/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "ui_settings_state.h"

namespace screensharing {

using namespace std;

UiSettingsState::UiSettingsState()
  : response_(-1) {
}

string UiSettingsState::app_locale_of(const string application_id) {
  auto it = app_locales_.find(application_id);
  return (it != app_locales_.end()) ? it->second : "";
}

vector<string> UiSettingsState::get_application_ids() {
  vector<string> application_ids;
  for (map<string, string>::const_iterator it = app_locales_.begin(); it != app_locales_.end(); it++) {
    application_ids.push_back(it->first);
  }
  return application_ids;
}

void UiSettingsState::add_unseen_app_locales(UiSettingsState* result) const {
  for (map<string, string>::const_iterator it = app_locales_.begin(); it != app_locales_.end(); it++) {
    if (result->app_locales_.count(it->first) == 0) {
      result->add_app_locale(it->first, it->second);
    }
  }
}

}  // namespace screensharing
