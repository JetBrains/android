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

#include "log.h"

#include <android/log.h>

#include "agent.h"
#include "string_printf.h"

namespace screensharing {

using namespace std;

namespace {

string FormatErrorMessage(const JThrowable* throwable, const char* message, va_list args) {
  string formattedMessage = StringVPrintf(message, args);
  return throwable == nullptr || throwable->IsNull() ? formattedMessage : formattedMessage + " - " + throwable->Describe();
}

void LogFatalError(const JThrowable* throwable, const char* message, va_list args) {
  string formattedMessage = FormatErrorMessage(throwable, message, args);
  __android_log_print(ANDROID_LOG_ERROR, ATTRIBUTION_TAG, "%s", formattedMessage.c_str());
  fprintf(stderr, "%s", formattedMessage.c_str());
}

}  // namespace

void Log::V(const char* message, ...) {
  if (IsEnabled(Level::VERBOSE)) {
    va_list args;
    va_start(args, message);
    __android_log_vprint(ANDROID_LOG_VERBOSE, ATTRIBUTION_TAG, message, args);
    va_end(args);
  }
}

void Log::D(const char* message, ...) {
  if (IsEnabled(Level::DEBUG)) {
    va_list args;
    va_start(args, message);
    __android_log_vprint(ANDROID_LOG_DEBUG, ATTRIBUTION_TAG, message, args);
    va_end(args);
  }
}

void Log::I(const char* message, ...) {
  if (IsEnabled(Level::INFO)) {
    va_list args;
    va_start(args, message);
    __android_log_vprint(ANDROID_LOG_INFO, ATTRIBUTION_TAG, message, args);
    va_end(args);
  }
}

void Log::W(const char* message, ...) {
  if (IsEnabled(Level::WARN)) {
    va_list args;
    va_start(args, message);
    string formattedMessage = FormatErrorMessage(nullptr, message, args);
    va_end(args);
    __android_log_print(ANDROID_LOG_WARN, ATTRIBUTION_TAG, "%s", formattedMessage.c_str());
  }
}

void Log::W(JThrowable throwable, const char* message, ...) {
  if (IsEnabled(Level::WARN)) {
    va_list args;
    va_start(args, message);
    string formattedMessage = FormatErrorMessage(&throwable, message, args);
    va_end(args);
    __android_log_print(ANDROID_LOG_WARN, ATTRIBUTION_TAG, "%s", formattedMessage.c_str());
  }
}

void Log::E(const char* message, ...) {
  va_list args;
  va_start(args, message);
  string formattedMessage = FormatErrorMessage(nullptr, message, args);
  va_end(args);
  __android_log_print(ANDROID_LOG_ERROR, ATTRIBUTION_TAG, "%s", formattedMessage.c_str());
}

void Log::E(JThrowable throwable, const char* message, ...) {
  va_list args;
  va_start(args, message);
  string formattedMessage = FormatErrorMessage(&throwable, message, args);
  va_end(args);
  __android_log_print(ANDROID_LOG_ERROR, ATTRIBUTION_TAG, "%s", formattedMessage.c_str());
}

void Log::Fatal(ExitCode exit_code, const char* message, ...) {
  va_list args;
  va_start(args, message);
  LogFatalError(nullptr, message, args);
  va_end(args);
  Agent::Shutdown();
  Jvm::Exit(exit_code);
}

void Log::Fatal(ExitCode exit_code, JThrowable throwable, const char* message, ...) {
  va_list args;
  va_start(args, message);
  LogFatalError(&throwable, message, args);
  va_end(args);
  Agent::Shutdown();
  Jvm::Exit(exit_code);
}

Log::Level Log::level_ = Log::Level::INFO;

}  // namespace screensharing
