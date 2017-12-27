/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixGradleVersionInWrapperHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.Collections;
import java.util.List;

import static com.intellij.ide.BrowserUtil.browse;

public class Gradle4AndPlugin2Dot2ErrorHandler extends SyncErrorHandler {
  // See https://issuetracker.google.com/37508452
  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    if (isGradleAndPluginMismatch(getRootCause(error), project)) {
      String migrationDocUrl = "https://goo.gl/sEn5eA";
      notification.setListener(migrationDocUrl, new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
          browse(migrationDocUrl);
        }
      });

      NotificationHyperlink fixGradleVersion = FixGradleVersionInWrapperHyperlink.createIfProjectUsesGradleWrapper(project, "3.5");

      String text = "The versions of the Android Gradle plugin and Gradle are not compatible.\n" +
                    "Please do one of the following:<ul>" +
                    "<li>Update your plugin to version 2.4. This will require changes to build.gradle due to API changes.\n" +
                    "<a href='" + migrationDocUrl + "'>Open migration guide</a></li>" +
                    "<li>Downgrade Gradle to version 3.5.";
      if (fixGradleVersion != null) {
        text += "\n" + fixGradleVersion.toHtml();
      }
      text += "</li></ul>";
      notification.setTitle(SyncMessage.DEFAULT_GROUP);
      notification.setMessage(text);
      notification.setNotificationCategory(NotificationCategory.convert(DEFAULT_NOTIFICATION_TYPE));

      List<NotificationHyperlink> quickFixes = Collections.emptyList();
      if (fixGradleVersion != null) {
        quickFixes = Collections.singletonList(fixGradleVersion);
      }

      GradleSyncMessages.getInstance(project).addNotificationListener(notification, quickFixes);
      return true;
    }
    return false;
  }

  private static boolean isGradleAndPluginMismatch(@NotNull Throwable rootCause, @NotNull Project project) {
    if (rootCause instanceof NoSuchMethodError) {
      // When this exception is thrown we don't have a reliable way to check the version of the plugin being used because Gradle Sync never
      // finished.
      String text = rootCause.getMessage();
      boolean targetMissingMethodFound = text.startsWith("com.android.build.gradle.tasks.factory.AndroidJavaCompile.setDependencyCacheDir");
      if (targetMissingMethodFound) {
        GradleWrapper gradleWrapper = GradleWrapper.find(project);
        if (gradleWrapper != null) {
          try {
            String version = gradleWrapper.getGradleVersion();
            if (version != null) {
              GradleVersion parsed = GradleVersion.parse(version);
              return parsed.compareIgnoringQualifiers("4.0") >= 0;
            }
          }
          catch (Exception ignored) {
          }
        }
        // We don't know the version of Gradle (very unlikely to happen.) Finding the missing method is good enough clue that
        // this is bug https://issuetracker.google.com/37508452
        return true;
      }
    }
    return false;
  }
}
