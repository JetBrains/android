/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.util.AndroidBundle;

public class ComposeInBetaChecker {
  private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup(
    "Jetpack Compose Project Notification",
    NotificationDisplayType.STICKY_BALLOON,
    true);

  public static void checkIfComposeProject(Project project) {
    if (NotificationsManager
          .getNotificationsManager()
          .getNotificationsOfType(ComposeProjectNotification.class, project).length > 0) {
      return;
    }
    Module[] modules = ModuleManager.getInstance(project).getModules();
    AndroidProjectSystem projectSystem = ProjectSystemService.getInstance(project).getProjectSystem();
    for (Module module : modules) {
      if (projectSystem.getModuleSystem(module).getUsesCompose()) {
        showNotification(project);
        return;
      }
    }
  }

  private static void showNotification(Project project) {
    Notification notification = new ComposeProjectNotification(
      AndroidBundle.message("compose.feature.in.non-canary.message"));
    notification.setTitle(AndroidBundle.message("compose.feature.in.non-canary.title"));
    notification.notify(project);
  }

  static class ComposeProjectNotification extends Notification {
    public ComposeProjectNotification(String content) {
      super(NOTIFICATION_GROUP.getDisplayId(), "Jetpack Compose project opened in non-Canary IDE", content, NotificationType.INFORMATION);
      setListener(NotificationListener.URL_OPENING_LISTENER);
    }
  }
}
