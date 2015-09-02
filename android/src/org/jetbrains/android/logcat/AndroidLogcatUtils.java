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

package org.jetbrains.android.logcat;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.run.LoggingReceiver;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

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
   *
   * @return A wrapper around the logcat reader and console writer, or {@code null} if logcat fails
   * to open. TODO: Should we return null or throw an exception instead?
   */
  @Nullable
  public static Pair<Reader, Writer> startLoggingThread(final Project project,
                                                        final IDevice device,
                                                        final boolean clearLogcat,
                                                        @NotNull final LogConsoleBase console) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        console.clear();
      }
    });
    PipedWriter logWriter = new PipedWriter();
    final AndroidLogcatReceiver receiver = new AndroidLogcatReceiver(device, logWriter);
    final PipedReader logReader;
    try {
      logReader = new PipedReader(logWriter) {
        @Override
        public void close() throws IOException {
          super.close();
          receiver.cancel();
        }

        @Override
        public synchronized boolean ready() {
          // We have to avoid Logging error in LogConsoleBase if logcat is finished incorrectly
          try {
            return super.ready();
          }
          catch (IOException e) {
            LOG.debug(e);
            return false;
          }
        }
      };
    }
    catch (IOException e) {
      LOG.info(e);
      console.writeToConsole("Unable to run logcat. IOException: " + e.getMessage() + '\n', ProcessOutputTypes.STDERR);
      return null;
    }
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
          console.writeToConsole(e.getMessage() + '\n', ProcessOutputTypes.STDERR);
        }
      }
    });
    return new Pair<Reader, Writer>(logReader, logWriter);
  }
}
