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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenProjectStructureHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DaemonContextMismatchErrorHandler extends AbstractSyncErrorHandler {
  private static final String JAVA_HOME = "javaHome=";

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);
    if (firstLine != null &&
        firstLine.contains("The newly created daemon process has a different context than expected.") &&
        message.size() > 3 && "Java home is different.".equals(message.get(2))) {
      String expectedAndActual = parseExpectedAndActualJavaHomes(error.getMessage());
      if (StringUtil.isNotEmpty(expectedAndActual)) {
        String newMsg = firstLine + "\n" + message.get(2) + "\n" + expectedAndActual + "\n" +
          "Please configure the JDK to match the expected one.";

        NotificationHyperlink quickFix = OpenProjectStructureHyperlink.openJdkSettings(project);
        if (quickFix != null) {
          updateNotification(notification, project, newMsg, quickFix);
        }
        else {
          updateNotification(notification, project, newMsg);
        }
        return true;
      }
    }
    return false;
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
        if (StringUtil.isNotEmpty(expected)) {
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
}
