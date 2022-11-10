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

#include "scoped_setting.h"

namespace screensharing {

using namespace std;

ScopedSetting::ScopedSetting(Settings::Table table, string key)
    : table_(table),
      key_(move(key)),
      restore_required_(false) {
}

ScopedSetting::~ScopedSetting() {
  if (restore_required_) {
    Settings::Put(table_, key_.c_str(), saved_value_.c_str());
  }
}

void ScopedSetting::Set(const char* value) {
  if (restore_required_) {
    Settings::Put(table_, key_.c_str(), value);
    if (saved_value_ == value) {
      restore_required_ = false;
    }
  } else {
    auto previous_value = Settings::Get(table_, key_.c_str());
    if (previous_value != value) {
      Settings::Put(table_, key_.c_str(), value);
      saved_value_ = previous_value;
      restore_required_ = true;
    }
  }
}

}  // namespace screensharing
