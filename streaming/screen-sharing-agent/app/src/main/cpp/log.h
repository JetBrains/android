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

#include "jvm.h"

namespace screensharing {

enum ExitCode {
  GENERIC_FAILURE = EXIT_FAILURE,
  INVALID_COMMAND_LINE = 2,
  SOCKET_CONNECTIVITY_ERROR = 10,
  SOCKET_IO_ERROR = 11,
  INVALID_CONTROL_MESSAGE = 12,
  NULL_POINTER = 20,
  CLASS_NOT_FOUND = 21,
  METHOD_NOT_FOUND = 22,
  CONSTRUCTOR_NOT_FOUND = 23,
  FIELD_NOT_FOUND = 24,
  JAVA_EXCEPTION = 25,
  VIDEO_ENCODER_NOT_FOUND = 30,
  VIDEO_ENCODER_INITIALIZATION_ERROR = 31,
  VIDEO_ENCODER_CONFIGURATION_ERROR = 32,
  WEAK_VIDEO_ENCODER = 33,
  REPEATED_VIDEO_ENCODER_ERRORS = 34,
  VIDEO_ENCODER_START_ERROR = 35,
  VIRTUAL_DISPLAY_CREATION_ERROR = 50,
  INPUT_SURFACE_CREATION_ERROR = 51,
  SERVICE_NOT_FOUND = 52,
  KEY_CHARACTER_MAP_ERROR = 53,
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

  // Logs a warning message.
  static void W(JThrowable throwable, const char* msg, ...)
      __attribute__((format(printf, 2, 3)));

  // Logs an error message.
  static void E(const char* msg, ...)
      __attribute__((format(printf, 1, 2)));

  // Logs an error message.
  static void E(JThrowable throwable, const char* msg, ...)
      __attribute__((format(printf, 2, 3)));

  // Logs an error message and terminates the program with the given exit code.
  [[noreturn]] static void Fatal(ExitCode exit_code, const char* msg, ...)
      __attribute__((format(printf, 2, 3)));

  // Logs an error message and terminates the program with the given exit code.
  [[noreturn]] static void Fatal(ExitCode exit_code, JThrowable throwable, const char* msg, ...)
      __attribute__((format(printf, 3, 4)));

  static void SetLevel(Level level) {
    level_ = level;
  }

  static bool IsEnabled(Level level) {
    return level >= level_;
  }

  Log() = delete;

private:
  static Level level_;
};

#define TRACE Log::D("%s:%d", __FILE__, __LINE__)

}  // namespace screensharing
