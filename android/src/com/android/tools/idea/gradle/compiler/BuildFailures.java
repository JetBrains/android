/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.compiler;

import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class BuildFailures {
  private BuildFailures() {
  }

  public static boolean unresolvedDependenciesFound(@NotNull String errorMessage) {
    return errorMessage.contains("Could not resolve all dependencies");
  }

  public static void notifyUnresolvedDependenciesInOfflineMode(@NotNull Project project) {
    NotificationHyperlink disableOfflineModeHyperlink = new NotificationHyperlink("disable.gradle.offline.mode", "Disable offline mode") {
      @Override
      protected void execute(@NotNull Project project) {
        AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
        buildConfiguration.OFFLINE_MODE = false;
      }
    };
    NotificationListener notificationListener = new CustomNotificationListener(project, disableOfflineModeHyperlink);

    String title = "Unresolved Dependencies";
    String text = "Unresolved dependencies detected while building project in offline mode. Please disable offline mode and try again. " +
                  disableOfflineModeHyperlink.toString();

    AndroidGradleNotification.getInstance(project).showBalloon(title, text, NotificationType.ERROR, notificationListener);
  }

  public static boolean notifyIfMissingDependencyInOfflineMode(@NotNull Project project, @NotNull String errorMessage) {
    if (errorMessage.contains("Could not resolve all dependencies")) {
      NotificationHyperlink disableOfflineModeHyperlink = new NotificationHyperlink("disable.gradle.offline.mode", "Disable offline mode") {
        @Override
        protected void execute(@NotNull Project project) {
          AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
          buildConfiguration.OFFLINE_MODE = false;
        }
      };
      NotificationListener notificationListener = new CustomNotificationListener(project, disableOfflineModeHyperlink);

      String title = "Unresolved Dependencies";
      String text = "Unresolved dependencies detected while building project in offline mode. Please disable offline mode and try again. " +
                    disableOfflineModeHyperlink.toString();

      AndroidGradleNotification.getInstance(project).showBalloon(title, text, NotificationType.ERROR, notificationListener);
      return true;
    }
    return false;
  }
}