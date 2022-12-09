/*
 * Copyright (C) 2021 The Android Open Source Project
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

class Log {
public:
  enum class Level {
    VERBOSE, DEBUG, INFO, WARN, ERROR
  };

  // Logs a message at the verbose level.
  static void V(const char* msg, ...)
      __attribute__((format(printf, 1, 2)));

  // Logs a message at the debug level.
  static void D(const char* msg, ...)
      __attribute__((format(printf, 1, 2)));

  // Logs an informational message.
  static void I(const char* msg, ...)
      __attribute__((format(printf, 1, 2)));

  // Logs a warning message.
  static void W(const char* msg, ...)
      __attribute__((format(printf, 1, 2)));

  // Logs an error message.
  static void E(const char* msg, ...)
      __attribute__((format(printf, 1, 2)));

  // Logs an error message and terminates the program.
  [[noreturn]] static void Fatal(const char* msg, ...)
      __attribute__((format(printf, 1, 2)));

  static void SetLevel(Level level) {
    level_ = level;
  }

  static bool IsEnabled(Level level) {
    return level >= level_;
  }

private:
  static Level level_;

  Log() = delete;
};

#define TRACE Log::D("%s:%d", __FILE__, __LINE__)

}  // namespace screensharing
