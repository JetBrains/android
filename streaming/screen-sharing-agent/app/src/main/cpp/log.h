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

enum ExitCode {
  GENERIC_FAILURE = EXIT_FAILURE,
  INVALID_COMMAND_LINE = 2,
  WEAK_VIDEO_ENCODER = 3,
  REPEATED_VIDEO_ENCODER_ERRORS = 4,
  VIDEO_ENCODER_NOT_FOUND = 10,
  VIDEO_ENCODER_INITIALIZATION_ERROR = 11,
  VIDEO_ENCODER_CONFIGURATION_ERROR = 12,
  VIRTUAL_DISPLAY_CREATION_ERROR = 13,
  INPUT_SURFACE_CREATION_ERROR = 14,
  SERVICE_NOT_FOUND = 15,
  SOCKET_CONNECTIVITY_ERROR = 20,
  SOCKET_IO_ERROR = 21,
  NULL_POINTER = 30,
  CLASS_NOT_FOUND = 31,
  METHOD_NOT_FOUND = 32,
  CONSTRUCTOR_NOT_FOUND = 33,
  FIELD_NOT_FOUND = 34,
  JAVA_EXCEPTION = 35,
};

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

  // Logs an error message and terminates the program with exit code 1.
  [[noreturn]] static void Fatal(const char* msg, ...)
      __attribute__((format(printf, 1, 2)));

  // Logs an error message and terminates the program with the given exit code.
  [[noreturn]] static void Fatal(ExitCode exit_code, const char* msg, ...)
  __attribute__((format(printf, 2, 3)));

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
