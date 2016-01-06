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
package com.android.tools.idea.sdk;

import com.android.utils.IReaderLogger;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for integrating stream-based SDK manager UI with the Android Studio UI.
 *
 * @deprecated to be removed once {@link com.android.tools.idea.sdk.wizard.legacy.SmwOldApiDirectInstall} is removed
 */
@Deprecated
public abstract class SdkLoggerIntegration implements IReaderLogger {
  // Groups: 1=license-id
  private static Pattern sLicenceText = Pattern.compile("^\\s*License id:\\s*([a-z0-9-]+).*\\s*");
  // Groups: 1=progress values (%, ETA), 2=% int, 3=progress text
  private static Pattern sProgress1Text = Pattern.compile("^\\s+\\((([0-9]+)%,\\s*[^)]*)\\)(.*)\\s*");
  // Groups: 1=progress text, 2=progress values, 3=% int
  private static Pattern sProgress2Text = Pattern.compile("^\\s+(.+)\\s+\\((([0-9]+)%)\\)\\s*");
  // Groups: 1=task title
  private static Pattern sDownloadingComponentText = Pattern.compile("^\\s+(Downloading .*)\\s*$");

  private BackgroundableProcessIndicator myIndicator;
  private String myCurrLicense;
  private String myLastLine;

  public void setIndicator(BackgroundableProcessIndicator indicator) {
    myIndicator = indicator;
  }

  /**
   * Used by UpdaterData.acceptLicense() to prompt for license acceptance
   * when updating the SDK from the command-line.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public int readLine(@NotNull byte[] inputBuffer) throws IOException {
    if (myLastLine != null && myLastLine.contains("Do you accept the license")) {
      // Let's take a simple shortcut and simply reply 'y' for yes.
      inputBuffer[0] = 'y';
      inputBuffer[1] = 0;
      return 1;
    }
    inputBuffer[0] = 'n';
    inputBuffer[1] = 0;
    return 1;
  }

  @Override
  public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
    if (msgFormat == null && t != null) {
      if (myIndicator != null) myIndicator.setText2(t.toString());
      outputLine(t.toString());
    }
    else if (msgFormat != null) {
      if (myIndicator != null) myIndicator.setText2(String.format(msgFormat, args));
      outputLine(String.format(msgFormat, args));
    }
  }

  @Override
  public void warning(@NotNull String msgFormat, Object... args) {
    if (myIndicator != null) myIndicator.setText2(String.format(msgFormat, args));
    outputLine(String.format(msgFormat, args));
  }

  @Override
  public void info(@NotNull String msgFormat, Object... args) {
    if (myIndicator != null) myIndicator.setText2(String.format(msgFormat, args));
    outputLine(String.format(msgFormat, args));
  }

  @Override
  public void verbose(@NotNull String msgFormat, Object... args) {
    // Don't log verbose stuff in the background indicator.
    outputLine(String.format(msgFormat, args));
  }

  /**
   * This method takes the console output from the command-line updater.
   * It filters it to remove some verbose output that is not desirable here.
   * It also detects progress-bar like text and updates the dialog's progress
   * bar accordingly.
   */
  private void outputLine(@NotNull final String line) {
    myLastLine = line;
    try {
      // skip some of the verbose output such as license text & refreshing http sources
      Matcher m = sLicenceText.matcher(line);
      if (m.matches()) {
        myCurrLicense = m.group(1);
        return;
      }
      else if (myCurrLicense != null) {
        if (line.contains("Do you accept the license") && line.contains(myCurrLicense)) {
          myCurrLicense = null;
        }
        return;
      }
      else if (line.contains("Fetching http") ||
               line.contains("Fetching URL:") ||
               line.contains("Validate XML") ||
               line.contains("Parse XML") ||
               line.contains("---------")) {
        return;
      }

      if (parseWithPattern(sProgress1Text, line, 3, 1, 2) ||
          parseWithPattern(sProgress2Text, line, 1, 2, 3) ||
          parseWithPattern(sDownloadingComponentText, line, 1, -1, -1)) {
        return;
      }

      // This is invoked from a backgroundable task, only update text on the ui thread.
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          lineAdded(line);
        }
      });
    }
    catch (Exception ignore) {
    }
  }

  private boolean parseWithPattern(@NotNull Pattern pattern, @NotNull String line, int titleGroup, int progressTextGroup, int progressGroup) {
    int progInt = -1;

    Matcher m = pattern.matcher(line);
    if (m.matches()) {
      if (progressGroup >= 0) {
        // Groups: 1=progress values (%, ETA), 2=% int, 3=progress text
        try {
          progInt = Integer.parseInt(m.group(progressGroup));
        }
        catch (NumberFormatException ignore) {
          progInt = 0;
        }
      }
      final int fProgInt = progInt;
      final String fProgText2 = getGroupText(m, progressTextGroup);
      final String fProgText1 = getGroupText(m, titleGroup);

      // This is invoked from a backgroundable task, only update text on the ui thread.
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (fProgText1 != null) {
            setTitle(fProgText1);
          }

          if (fProgText2 != null) {
            setDescription(fProgText2);
          }

          if (fProgInt >= 0) {
            setProgress(fProgInt);
          }
        }
      });
      return true;
    }
    else {
      return false;
    }
  }

  @Nullable
  private static String getGroupText(Matcher m, int group) {
    return group >= 0 ? m.group(group) : null;
  }

  protected abstract void setProgress(int progress);

  protected abstract void setDescription(String description);

  protected abstract void setTitle(String title);

  protected abstract void lineAdded(String string);
}
