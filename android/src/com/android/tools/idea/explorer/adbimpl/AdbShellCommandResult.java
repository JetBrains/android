/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AdbShellCommandResult {
  @NotNull private String myCommand;
  @NotNull private final List<String> myOutput;
  private final boolean myError;

  public AdbShellCommandResult(@NotNull String command, @NotNull List<String> output, boolean isError) {
    myCommand = command;
    myOutput = output;
    myError = isError;
  }

  @NotNull
  public List<String> getOutput() {
    return myOutput;
  }

  public boolean isError() {
    return myError;
  }

  public void throwIfError() throws AdbShellCommandException {
    if (isError()) {
      // Shell commands that fail outright usually have a single output line containing the error message
      if (myOutput.size() == 1) {
        throw AdbShellCommandException.create(myOutput.get(0));
      }
      else {
        throw AdbShellCommandException.create("Error executing shell command %s", myCommand);
      }
    }
  }
}
