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

#include <string>

#include "agent.h"

namespace screensharing {

using namespace std;

static constexpr char TAG[] = "ScreenSharing";

Log::Level Log::level_ = Log::Level::INFO;

void Log::V(const char* message, ...) {
  if (IsEnabled(Level::VERBOSE)) {
    va_list args;
    va_start(args, message);
    __android_log_vprint(ANDROID_LOG_VERBOSE, TAG, message, args);
    va_end(args);
  }
}

void Log::D(const char* message, ...) {
  if (IsEnabled(Level::DEBUG)) {
    va_list args;
    va_start(args, message);
    __android_log_vprint(ANDROID_LOG_DEBUG, TAG, message, args);
    va_end(args);
  }
}

void Log::I(const char* message, ...) {
  if (IsEnabled(Level::INFO)) {
    va_list args;
    va_start(args, message);
    __android_log_vprint(ANDROID_LOG_INFO, TAG, message, args);
    va_end(args);
  }
}

void Log::W(const char* message, ...) {
  if (IsEnabled(Level::WARN)) {
    va_list args;
    va_start(args, message);
    __android_log_vprint(ANDROID_LOG_WARN, TAG, message, args);
    va_end(args);
  }
}

void Log::E(const char* message, ...) {
  va_list args;
  va_start(args, message);
  __android_log_vprint(ANDROID_LOG_ERROR, TAG, message, args);
  va_end(args);
}

void Log::Fatal(const char* message, ...) {
  va_list args;
  va_start(args, message);
  __android_log_vprint(ANDROID_LOG_ERROR, TAG, message, args);
  va_end(args);
  va_start(args, message);
  vfprintf(stderr, message, args);
  va_end(args);
  Agent::RestoreEnvironment();
  exit(1);
}

}  // namespace screensharing
