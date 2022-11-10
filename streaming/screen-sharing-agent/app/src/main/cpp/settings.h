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

namespace screensharing {

// Queries and modifies Android system settings.
class Settings {
public:
  enum class Table { SYSTEM, SECURE, GLOBAL };

  // Returns the value corresponding to the given key in the given table,
  // or an empty string if the key does not have an associated value.
  static std::string Get(Table table, const char* key);
  // Sets the value corresponding to the given key in the given table,
  // or deletes that value if the value string is empty.
  static void Put(Table table, const char* key, const char* value);

private:
  static const char* table_names_[];

  Settings() = delete;
};

}  // namespace screensharing
