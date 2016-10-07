/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.logcat.AndroidLogcatUtils;
import com.google.common.base.Strings;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A logcat listener base class used by the run and debug console windows
 */
public abstract class ApplicationLogListener implements AndroidLogcatService.LogLineListener {
  @Nullable private LogCatHeader myActiveHeader;
  @NotNull private final String myPackageName;
  private final int myPid;

  public ApplicationLogListener(@NotNull String packageName, int pid) {
    myPackageName = packageName;
    myPid = pid;
  }

  @Override
  public void receiveLogLine(@NotNull LogCatMessage line) {
    if (!myPackageName.equals(line.getHeader().getAppName()) || myPid != line.getHeader().getPid()) {
      myActiveHeader = null;
      return;
    }

    String message;
    if (!line.getHeader().equals(myActiveHeader)) {
      myActiveHeader = line.getHeader();
      message = formatLogLine(line);
    } else {
      message = Strings.repeat(" ", formatLogLine(line).indexOf(line.getMessage())) + line.getMessage();
    }

    Key key = AndroidLogcatUtils.getProcessOutputType(myActiveHeader.getLogLevel());
    notifyTextAvailable(message + "\n", key);
  }

  protected abstract String formatLogLine(@NotNull LogCatMessage line);

  protected abstract void notifyTextAvailable(@NotNull String message, @NotNull Key key);
}
