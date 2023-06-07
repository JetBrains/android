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
      key_(std::move(key)),
      current_value_known_(false) {
}

ScopedSetting::~ScopedSetting() {
  Restore();
}

void ScopedSetting::Restore() {
  if (current_value_ != original_value_) {
    Settings::Put(table_, key_.c_str(), original_value_.c_str());
    current_value_ = original_value_;
  }
}

void ScopedSetting::Set(const char* value) {
  if (!current_value_known_) {
    current_value_ = Settings::Get(table_, key_.c_str());
    original_value_ = current_value_;
    current_value_known_ = true;
  }
  if (value != current_value_) {
    Settings::Put(table_, key_.c_str(), value);
    current_value_ = value;
  }
}

}  // namespace screensharing
