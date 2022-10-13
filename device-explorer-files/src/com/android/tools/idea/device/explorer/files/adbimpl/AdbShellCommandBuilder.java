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
package com.android.tools.idea.device.explorer.files.adbimpl;

import static com.android.ddmlib.FileListingService.FILE_SEPARATOR;

import org.jetbrains.annotations.NotNull;

public class AdbShellCommandBuilder {
  @NotNull private final StringBuilder myCommand = new StringBuilder();
  private boolean mySuRootPrefix;
  private String myRunAsPackage;

  @Override
  public String toString() {
    return build();
  }

  @NotNull
  public String build() {
    // Make sure build does not have side-effects as it's called from toString
    // otherwise it breaks while debugging it.
    if (mySuRootPrefix && myRunAsPackage != null) {
      throw new IllegalStateException("Either so or run-as are supported, not both.");
    }

    String cmd =  myCommand.toString();
    if (mySuRootPrefix || myRunAsPackage != null) {
      // If using the path inside quotes, we need to further escape it
      cmd = cmd.replaceAll("'", "'\"\\'\"'");
    }

    if (mySuRootPrefix) {
      return "su 0 sh -c '" + cmd + "'";
    }
    if (myRunAsPackage != null) {
      return "run-as " + myRunAsPackage + " sh -c '" + cmd + "'";
    }
    return cmd;
  }

  @NotNull
  public AdbShellCommandBuilder withText(@NotNull String text) {
    myCommand.append(text);
    return this;
  }

  @NotNull
  public AdbShellCommandBuilder withSuRootPrefix() {
    mySuRootPrefix = true;
    return this;
  }


  public AdbShellCommandBuilder withRunAs(@NotNull String packageName) {
    myRunAsPackage = packageName;
    return this;
  }

  @NotNull
  public AdbShellCommandBuilder withEscapedPath(@NotNull String path) {
    myCommand.append(AdbPathUtil.getEscapedPath(path));
    return this;
  }

  /**
   * If we expect a file to behave like a directory, we should stick a "/" at the end.
   * This is a good habit, and is mandatory for symlinks-to-directories, which will
   * otherwise behave like symlinks.
   */
  @NotNull
  public AdbShellCommandBuilder withDirectoryEscapedPath(@NotNull String path) {
    path = AdbPathUtil.getEscapedPath(path);
    if (!path.endsWith(FILE_SEPARATOR)) {
      path += FILE_SEPARATOR;
    }
    myCommand.append(path);
    return this;
  }
}
