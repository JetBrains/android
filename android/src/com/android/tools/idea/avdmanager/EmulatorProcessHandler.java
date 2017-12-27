/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The {@link EmulatorProcessHandler} is a custom process handler specific to handling
 * the emulator process. The majority of the code is derived from {@link com.intellij.execution.process.BaseOSProcessHandler}.
 */
public class EmulatorProcessHandler extends BaseOSProcessHandler{
  private static final Logger LOG = Logger.getInstance(EmulatorProcessHandler.class);

  @NotNull private final Process myProcess;
  @NotNull private final GeneralCommandLine myCommandLine;
  @NotNull private final ConsoleListner myConsoleListner;

  public EmulatorProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    super(process, commandLine.getCommandLineString(), null);
    myProcess = process;
    myCommandLine = commandLine;
    myConsoleListner = new ConsoleListner();
    addProcessListener(myConsoleListner);
    ProcessTerminatedListener.attach(this);
  }

  private class ConsoleListner extends ProcessAdapter {

    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      String text = event.getText();
      String content = String.format("%s: %s", AndroidBundle.message("android.emulator"), text.trim());
      if (ProcessOutputTypes.SYSTEM.equals(outputType) && EmulatorProcessHandler.this.isProcessTerminated()) {
        Integer exitCode = EmulatorProcessHandler.this.getExitCode();
        if (exitCode != null) {
          Notification notification = new Notification(AndroidBundle.message("android.emulator"),
                                                       "",
                                                       content,
                                                       (exitCode == 0) ? NotificationType.INFORMATION : NotificationType.ERROR);
          Notifications.Bus.notify(notification);
        }
        return;
      }
      boolean hasError = text != null && text.toLowerCase(Locale.US).contains("error");
      if (hasError || ProcessOutputTypes.STDERR.equals(outputType)) {
        Notification notification = new Notification(AndroidBundle.message("android.emulator"),
                                                     "",
                                                     content,
                                                     NotificationType.ERROR);

        Notifications.Bus.notify(notification);
      }
    }
  }
}
