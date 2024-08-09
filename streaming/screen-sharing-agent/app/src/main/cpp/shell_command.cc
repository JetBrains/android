/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "shell_command.h"

namespace screensharing {

using namespace std;

ShellCommand ShellCommand::operator+(const ShellCommand& other) const {
  ShellCommand result(*this);
  return result += other;
}

ShellCommand& ShellCommand::operator+=(const ShellCommand& other) {
  if (!empty() && !other.empty()) {
    append(SEPARATOR);
  }
  append(other);
  return *this;
}

}  // namespace screensharing
