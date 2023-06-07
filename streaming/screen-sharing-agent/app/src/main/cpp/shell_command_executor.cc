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

#include "shell_command_executor.h"

#include "log.h"

namespace screensharing {

using namespace std;

string ExecuteShellCommand(const char* command) {
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
  Log::D(R"(Shell command "%s" produced "%s")", command, output.c_str());
  return output;
}

}  // namespace screensharing
