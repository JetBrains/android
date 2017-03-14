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

import static com.android.ddmlib.FileListingService.FILE_SEPARATOR;

public class AdbShellCommandBuilder {
  @NotNull private final StringBuilder myCommand = new StringBuilder();
  private boolean myNeedsTrailingQuote;

  @Override
  public String toString() {
    return build();
  }

  @NotNull
  public String build() {
    if (myNeedsTrailingQuote) {
      myCommand.append("'");
    }
    return myCommand.toString();
  }

  @NotNull
  public AdbShellCommandBuilder withText(@NotNull String text) {
    myCommand.append(text);
    return this;
  }

  @NotNull
  public AdbShellCommandBuilder withSuRootPrefix() {
    if (myCommand.length() > 0) {
      throw new IllegalStateException("\"su 0\" must be the first argument");
    }
    myCommand.append("su 0 sh -c '");
    myNeedsTrailingQuote = true;
    return this;
  }


  public AdbShellCommandBuilder withRunAs(@NotNull String packageName) {
    if (myCommand.length() > 0) {
      throw new IllegalStateException("\"run-as\" must be the first argument");
    }
    withText("run-as ").withText(packageName).withText(" sh -c '");
    myNeedsTrailingQuote = true;
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
