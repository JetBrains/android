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

#pragma once

#include <string>

namespace screensharing {

// Represents a shell command.
struct ShellCommand : public std::string {
  // The separator used when concatenating shell commands.
  static constexpr char const SEPARATOR[] = ";\n";

  using std::string::string;
  ShellCommand(std::string&& str)
      : std::string(str) {}

  // Concatenates two shell commands adding a SEPARATOR between them.
  ShellCommand operator+(const ShellCommand& other) const;
  // Appends a shell command to this one adding a SEPARATOR in between.
  ShellCommand& operator+=(const ShellCommand& other);
};

}  // namespace screensharing
