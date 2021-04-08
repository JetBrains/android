/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.intellij.diagnostic.logging.LogConsoleBase;
import org.jetbrains.annotations.NotNull;

final class ViewListener extends FormattedLogcatReceiver {
  private final AndroidLogcatView myView;
  private final SafeAlarm myAlarm;

  ViewListener(@NotNull AndroidLogcatFormatter formatter, @NotNull AndroidLogcatView view) {
    super(formatter);

    myView = view;
    myAlarm = new SafeAlarm(view.parentDisposable);
  }

  @Override
  void receiveFormattedLogLine(@NotNull String line) {
    myView.getLogConsole().addLogLine(line);

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myView.getToolbar()::updateActionsImmediately, 50);
  }

  @Override
  public void onCleared() {
    myView.getLogFilterModel().beginRejectingOldMessages();
    LogConsoleBase console = myView.getLogConsole();

    if (console.getConsole() == null) {
      return;
    }

    console.clear();
  }
}
