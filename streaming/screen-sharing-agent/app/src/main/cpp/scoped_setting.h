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

#pragma once

#include <string>

#include "common.h"
#include "settings.h"

namespace screensharing {

// An Android system setting that can be modified and then restored to the original value when
// the ScopedSetting object is destroyed.
class ScopedSetting {
public:
  ScopedSetting(Settings::Table table, std::string key);
  ~ScopedSetting();

  void Set(const char* value);
  void Restore();

private:
  Settings::Table table_;
  const std::string key_;
  bool restore_required_;
  std::string saved_value_;

  DISALLOW_COPY_AND_ASSIGN(ScopedSetting);
};

}  // namespace screensharing
