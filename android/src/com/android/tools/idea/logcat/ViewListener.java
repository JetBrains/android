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

import static com.intellij.util.Alarm.ThreadToUse.SWING_THREAD;

import com.android.ddmlib.logcat.LogCatMessage;
import com.intellij.diagnostic.logging.LogConsoleBase;
import org.jetbrains.annotations.NotNull;

/**
 * A logcat receiver that adds lines to an {@link AndroidLogcatView}.
 * <p>
 * As each line is received, it also updates the toolbar actions state. This is needed because the
 * IDEA framework will only update the actions state as long as the UI is being interacted with.
 * But the nature of the Logcat window is that users will typically just watch it and wait for
 * something to be logged. See https://issuetracker.google.com/71689829.
 * <p>
 * The update is posted to the Event Dispatch Thread (EDT) using an {@link com.intellij.util.Alarm}
 * no more frequently than every 500ms which is the normal refresh rate.
 */
final class ViewListener implements AndroidLogcatService.LogcatListener {
  private static final int DELAY_MS = 500;
  private final AndroidLogcatView myView;
  private final SafeAlarm myAlarm;

  ViewListener(@NotNull AndroidLogcatView view) {
    myView = view;
    // It seems that updateActionsImmediately() needs to run on the EDT.
    myAlarm = new SafeAlarm(SWING_THREAD, view.parentDisposable);
  }

  @Override
  public void onLogLineReceived(@NotNull LogCatMessage line) {
    myView.getLogConsole().addLogLine(LogcatJson.toJson(line));

    // If we already added a request, we just let it run. This is better than canceling it and
    // scheduling another one which can result in starvation.
    myAlarm.addRequestIfNotEmpty(myView.getToolbar()::updateActionsImmediately, DELAY_MS);
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
