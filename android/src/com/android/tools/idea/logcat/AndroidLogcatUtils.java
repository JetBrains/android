/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.logcat;

import com.android.ddmlib.*;
import com.android.tools.idea.run.LoggingReceiver;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class AndroidLogcatUtils {
  private static final Logger LOG = Logger.getInstance(AndroidLogcatUtils.class);

  private AndroidLogcatUtils() {
  }

  private static void startLogging(IDevice device, AndroidOutputReceiver receiver)
    throws IOException, ShellCommandUnresponsiveException, AdbCommandRejectedException, TimeoutException {
    AndroidUtils.executeCommandOnDevice(device, "logcat -v long", receiver, true);
  }

  static void clearLogcat(@Nullable final Project project, @NotNull IDevice device) {
    try {
      AndroidUtils.executeCommandOnDevice(device, "logcat -c", new LoggingReceiver(LOG), false);
    }
    catch (final Exception e) {
      LOG.info(e);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(project, "Error: " + e.getMessage(), AndroidBundle.message("android.logcat.error.dialog.title"));
        }
      });
    }
  }

  /**
   * Starts a thread which reads data from Android logging output and writes a processed view of
   * the data out to a console.
   */
  @NotNull
  public static AndroidLogcatReceiver startLoggingThread(final Project project,
                                                        final IDevice device,
                                                        final boolean clearLogcat,
                                                        @NotNull final AndroidConsoleWriter writer) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        writer.clear();
      }
    });
    final AndroidLogcatReceiver receiver = new AndroidLogcatReceiver(device, writer);
    Disposer.register(project, receiver);
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        if (clearLogcat) {
          clearLogcat(project, device);
        }
        try {
          startLogging(device, receiver);
        }
        catch (final Exception e) {
          LOG.info(e);
          writer.addMessage(AndroidLogcatFormatter.formatMessage(Log.LogLevel.ERROR, e.getMessage()));
        }
      }
    });
    return receiver;
  }
}
