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

#include <cstdarg>
#include <string>

namespace screensharing {

// Formats a string and returns it.
std::string StringPrintf(const char* format, ...) __attribute__((format(printf, 1, 2)));
std::string StringVPrintf(const char* format, va_list args);

// Returns contents of a buffer as hexadecimal string.
std::string HexString(const void* buf, size_t size);

}  // namespace screensharing
