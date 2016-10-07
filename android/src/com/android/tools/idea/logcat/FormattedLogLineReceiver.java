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
package com.android.tools.idea.logcat;

import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import org.jetbrains.annotations.NotNull;

abstract class FormattedLogLineReceiver implements AndroidLogcatService.LogLineListener {
  private LogCatHeader myActiveHeader;

  @Override
  public final void receiveLogLine(@NotNull LogCatMessage line) {
    if (!line.getHeader().equals(myActiveHeader)) {
      myActiveHeader = line.getHeader();
      String message = AndroidLogcatFormatter.formatMessageFull(myActiveHeader, line.getMessage());
      receiveFormattedLogLine(message);
    } else {
      String message = AndroidLogcatFormatter.formatContinuation(line.getMessage());
      receiveFormattedLogLine(message);
    }
  }

  protected abstract void receiveFormattedLogLine(@NotNull String line);
}
