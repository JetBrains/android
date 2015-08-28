/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ddms.adb;

import com.android.ddmlib.Log;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.Nullable;

/** {@link AdbLogOutput} redirects the output from {@link Log.ILogOutput} to IDEA's log files. */
public class AdbLogOutput implements Log.ILogOutput {
  private static final NotificationGroup EVENT_LOG = NotificationGroup.logOnlyGroup("ADB Logs");
  private static final Logger LOG = Logger.getInstance("#com.android.ddmlib");

  @Override
  public void printLog(Log.LogLevel logLevel, String tag, String message) {
    reportAdbLog(logLevel, tag, message);
  }

  @Override
  public void printAndPromptLog(Log.LogLevel logLevel, String tag, String message) {
    reportAdbLog(logLevel, tag, message);
  }

  private static void reportAdbLog(@Nullable Log.LogLevel logLevel, @Nullable String tag, @Nullable String message) {
    if (message == null) {
      return;
    }

    if (logLevel == null) {
      logLevel = Log.LogLevel.DEBUG;
    }

    switch (logLevel) {
      case VERBOSE:
      case DEBUG:
        LOG.debug(message);
        break;
      case INFO:
        LOG.info(message);
        break;
      case WARN:
        LOG.warn(message);
        break;
      case ERROR:
      case ASSERT:
        // Note: These aren't programming errors, but setup/installation errors. So we inform the user via an entry in the
        // event log, and append to the system log file so that we can potentially inspect and understand bug reports.
        LOG.warn(message);
        EVENT_LOG.createNotification(message, MessageType.ERROR).notify(null);
        break;
    }
  }
}
