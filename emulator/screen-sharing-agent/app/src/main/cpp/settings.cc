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

#include "settings.h"

#include "log.h"

namespace screensharing {

using namespace std;

namespace {

// Invokes a shell command and returns its output. Returns when the command finishes execution.
string ExecuteCommand(const char* command) {
  string output;
  FILE* stream = popen(command, "r");
  if (stream != nullptr) {
    char buffer[256];
    while (!feof(stream)) {
      if (fgets(buffer, size(buffer), stream) != nullptr) {
        output.append(buffer);
      }
    }
    auto retcode = pclose(stream);
    if (retcode != 0) {
      Log::E("\"%s\" returned %d", command, retcode);
    }
  }
  return output;
}

}  // namespace

std::string Settings::Get(Settings::Table table, const char* key) {
  string command = string("cmd settings get ") + table_names_[static_cast<int>(table)] + " " + key;
  string value = ExecuteCommand(command.c_str());
  while (!value.empty() && value.back() <= ' ') {
    value.erase(value.size() - 1);
  }
  return value == "null" ? "" : value;
}

void Settings::Put(Settings::Table table, const char* key, const char* value) {
  string command = *value == 0 ?
      string("cmd settings delete ") + table_names_[static_cast<int>(table)] + " " + key :
      string("cmd settings put ") + table_names_[static_cast<int>(table)] + " " + key + " " + value;
  ExecuteCommand(command.c_str());
}

const char* Settings::table_names_[] = {"system", "secure", "global" };

}  // namespace screensharing
