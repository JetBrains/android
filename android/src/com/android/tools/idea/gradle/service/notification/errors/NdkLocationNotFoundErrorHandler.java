/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.SelectNdkDialog;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class NdkLocationNotFoundErrorHandler extends AbstractSyncErrorHandler {
  private static final Logger LOG = Logger.getInstance(NdkLocationNotFoundErrorHandler.class);

  private static final String ERROR_TITLE = "Gradle Sync Error";

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);

    if (firstLine.startsWith("NDK location not found.")) {
      List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
      NotificationHyperlink selectNdkLink = getSelectNdkNotificationHyperlink(true);
      hyperlinks.add(selectNdkLink);
      updateNotification(notification, project, "Android NDK location is not specified.", hyperlinks);
      return true;
    }
    return false;
  }

  public static NotificationHyperlink getSelectNdkNotificationHyperlink(final boolean showDownloadLink) {
    return new NotificationHyperlink("ndk.select", "Select NDK") {
      @Override
      protected void execute(@NotNull Project project) {
        File path = getNdkPath(project);
        SelectNdkDialog dialog = new SelectNdkDialog(path == null ? null : path.getPath(), false, showDownloadLink);
        dialog.setModal(true);
        if(dialog.showAndGet()) { // User clicked OK.
          if (setNdkPath(project, dialog.getAndroidNdkPath())) { // Saving NDK path is successful.
            GradleProjectImporter.getInstance().requestProjectSync(project, null);
          }
        }
      }
    };
  }

  @Nullable
  private static File getNdkPath(@NotNull Project project) {
    try {
      return new LocalProperties(project).getAndroidNdkPath();
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file of Project '%1$s'", project.getName());
      LOG.info(msg, e);
    }
    return null;
  }

  private static boolean setNdkPath(@NotNull Project project, @Nullable String ndkPath) {
    LocalProperties localProperties;
    try {
      localProperties = new LocalProperties(project);
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file of Project '%1$s':\n%2$s", project.getName(), e.getMessage());
      Messages.showErrorDialog(msg, ERROR_TITLE);
      return false;
    }
    try {
      localProperties.setAndroidNdkPath(ndkPath == null ? null : new File(ndkPath));
      localProperties.save();
    }
    catch (IOException e) {
      String msg = String.format("Unable to save local.properties file of Project '%1$s: %2$s", localProperties.getPropertiesFilePath().getPath(),
                                 e.getMessage());
      Messages.showErrorDialog(msg, ERROR_TITLE);
      return false;
    }
    return true;
  }
}
