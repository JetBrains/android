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

#include "string_printf.h"

#include "log.h"

namespace screensharing {

using namespace std;

string StringPrintf(const char* format, ...) {
  va_list args;
  va_start(args, format);
  string result = StringVPrintf(format, args);
  va_end(args);
  return result;
}

string StringVPrintf(const char* format, va_list args) {
  // Since va_list can be used only once, make a copy of it for use in one of the two vsnprintf calls.
  va_list args_copy;
  va_copy(args_copy, args);
  int size_s = vsnprintf(nullptr, 0, format, args_copy);
  if (size_s <= 0) {
    throw runtime_error("Error during formatting.");
  }
  auto size = static_cast<size_t>(size_s) + 1;  // Extra space for '\0'.
  auto buf = make_unique<char[]>(size);
  vsnprintf(buf.get(), size, format, args);
  return string(buf.get(), buf.get() + size - 1);  // Without the '\0'.
}

}  // namespace screensharing
