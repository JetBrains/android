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
import com.android.tools.idea.gradle.project.sync.hyperlink.BuildProjectHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenAndroidSdkManagerHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

// See https://code.google.com/p/android/issues/detail?id=72556
public class ObjectStreamErrorHandler extends SyncErrorHandler {
  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    String text = findErrorMessage(error);
    if (text != null) {
      findAndAddQuickFixes(notification, project, text);
      return true;
    }
    return false;
  }

  @Nullable
  private static String findErrorMessage(@NotNull Throwable rootCause) {
    String text = rootCause.getMessage();
    if (isNotEmpty(text) && getFirstLineMessage(text).endsWith("unexpected end of block data")) {
      updateUsageTracker();
      String newMsg = "An unexpected I/O error occurred.\n";
      newMsg += String.format("The error, \"%1$s\" usually happens on Linux when Build-tools or an Android platform being used in a " +
                              "project is not installed.\n", text);
      return newMsg;
    }
    return null;
  }

  private static void findAndAddQuickFixes(@NotNull NotificationData notification, @NotNull Project project, @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();

    NotificationHyperlink buildProjectHyperlink = new BuildProjectHyperlink();
    NotificationHyperlink openAndroidSdkManagerHyperlink = new OpenAndroidSdkManagerHyperlink();
    text += "Please try one of the following:<ul>" +
            "<li>" + buildProjectHyperlink.toHtml() + " to obtain the cause of the error</li>" +
            "<li>" + openAndroidSdkManagerHyperlink.toHtml() + " to check if there are any missing components</li></ul>";

    hyperlinks.add(buildProjectHyperlink);
    hyperlinks.add(openAndroidSdkManagerHyperlink);

    notification.setTitle(SyncMessage.DEFAULT_GROUP);
    notification.setMessage(text);
    notification.setNotificationCategory(NotificationCategory.convert(DEFAULT_NOTIFICATION_TYPE));

    GradleSyncMessages.getInstance(project).addNotificationListener(notification, hyperlinks);
  }
}