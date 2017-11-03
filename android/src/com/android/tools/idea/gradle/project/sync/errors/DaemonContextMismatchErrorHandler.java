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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.annotations.Nullable;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenProjectStructureHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

// See https://code.google.com/p/android/issues/detail?id=76984
public class DaemonContextMismatchErrorHandler extends BaseSyncErrorHandler {
  private static final String JAVA_HOME = "javaHome=";

  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();
    List<String> message = getMessageLines(text);
    if (message.isEmpty()) {
      return null;
    }
    String firstLine = message.get(0);
    if (isNotEmpty(firstLine) &&
        firstLine.contains("The newly created daemon process has a different context than expected.") &&
        message.size() > 3 &&
        "Java home is different.".equals(message.get(2))) {
      String expectedAndActual = parseExpectedAndActualJavaHomes(text);
      if (isNotEmpty(expectedAndActual)) {
        updateUsageTracker();
        return firstLine + "\n" + message.get(2) + "\n" + expectedAndActual + "\n" + "Please configure the JDK to match the expected one.";
      }
    }
    return null;
  }

  @VisibleForTesting
  @Nullable
  static String parseExpectedAndActualJavaHomes(@NotNull String errorMsg) {
    int startIndex = errorMsg.indexOf(JAVA_HOME);
    if (startIndex != -1) {
      startIndex += JAVA_HOME.length();
      int endIndex = errorMsg.indexOf(',', startIndex);
      if (endIndex != -1 && endIndex > startIndex) {
        String expected = errorMsg.substring(startIndex, endIndex);
        if (isNotEmpty(expected)) {
          String actual = null;
          startIndex = errorMsg.indexOf(JAVA_HOME, endIndex);
          if (startIndex != -1) {
            startIndex += JAVA_HOME.length();
            endIndex = errorMsg.indexOf(',', startIndex);
            if (endIndex != -1 && endIndex > startIndex) {
              actual = errorMsg.substring(startIndex, endIndex);
            }
          }

          String s = String.format("Expecting: '%1$s'", expected);
          if (actual != null) {
            s += String.format(" but was: '%1$s'.", actual);
          }
          else {
            s += ".";
          }
          return s;
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    hyperlinks.add(OpenProjectStructureHyperlink.openJdkSettings(project));
    return hyperlinks;
  }
}