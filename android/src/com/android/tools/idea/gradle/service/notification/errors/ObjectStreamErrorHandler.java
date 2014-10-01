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

import com.android.tools.idea.gradle.service.notification.hyperlink.BuildProjectHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenAndroidSdkManagerHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ObjectStreamErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);
    if (firstLine.endsWith("unexpected end of block data")) {
      NotificationHyperlink buildProjectHyperlink = new BuildProjectHyperlink();
      NotificationHyperlink openAndroidSdkManagerHyperlink = new OpenAndroidSdkManagerHyperlink();

      String msg = "An unexpected I/O error occurred.\n";
      msg += String.format("The error, \"%1$s\" usually happens on Linux when Build-tools or an Android platform being used in a " +
                           "project is not installed.\n", message);
      msg += "Please try one of the following:<ul>" +
             "<li>" + buildProjectHyperlink.toHtml() + " to obtain the cause of the error</li>" +
             "<li>" + openAndroidSdkManagerHyperlink.toHtml() + " to check if there are any missing components</li></ul>";

      String title = String.format(FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT, project.getName());
      notification.setTitle(title);
      notification.setMessage(msg);
      notification.setNotificationCategory(NotificationCategory.convert(DEFAULT_NOTIFICATION_TYPE));
      addNotificationListener(notification, project, buildProjectHyperlink, openAndroidSdkManagerHyperlink);
      return true;
    }

    return false;
  }
}
