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

#include "string_util.h"

#include <limits>

namespace screensharing {

using namespace std;

string RTrim(string str) {
  str.erase(find_if(str.rbegin(), str.rend(), [](unsigned char ch) {
    return !isspace(ch);
  }).base(), str.end());
  return str;
}

int32_t ParseInt(const char* str, int32_t def_value) {
  char* ptr;
  long result = strtol(str, &ptr, 10);
  if (*ptr != '\0' || result < numeric_limits<int32_t>::min() || result > numeric_limits<int32_t>::max()) {
    return def_value;
  }
  return result;
}

int32_t ParseInt(const std::string& str, int32_t def_value) {
  return ParseInt(str.c_str(), def_value);
}

}  // namespace screensharing
