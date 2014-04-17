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
package com.android.tools.idea.gradle.messages;

import com.android.tools.idea.gradle.messages.navigatable.ProjectSyncErrorNavigatable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Service that collects and displays, in the "Messages" tool window, post-sync project setup messages (errors, warnings, etc.)
 */
public class ProjectSyncMessages {

  @NotNull private final Project myProject;
  @NotNull private final ExternalSystemNotificationManager myNotificationManager;

  @NotNull
  public static ProjectSyncMessages getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectSyncMessages.class);
  }

  public int getErrorCount() {
    return myNotificationManager.getMessageCount(
      null, NotificationSource.PROJECT_SYNC, NotificationCategory.ERROR, GradleConstants.SYSTEM_ID);
  }

  public ProjectSyncMessages(@NotNull Project project, @NotNull ExternalSystemNotificationManager manager) {
    myProject = project;
    myNotificationManager = manager;
  }

  public void removeMessages(@NotNull String groupName) {
    myNotificationManager.clearNotifications(groupName, NotificationSource.PROJECT_SYNC, GradleConstants.SYSTEM_ID);
  }

  public int getMessageCount(@NotNull String groupName) {
    return myNotificationManager.getMessageCount(groupName, NotificationSource.PROJECT_SYNC, null, GradleConstants.SYSTEM_ID);
  }

  public void add(@NotNull final Message message) {
    Navigatable navigatable = message.getNavigatable();
    if (navigatable instanceof ProjectSyncErrorNavigatable) {
      ((ProjectSyncErrorNavigatable)navigatable).setProject(myProject);
    }
    NotificationData notification = new NotificationData(
      message.getGroupName(), StringUtil.join(message.getText(), "\n"),
      NotificationCategory.convert(message.getType().getValue()), NotificationSource.PROJECT_SYNC);
    notification.setNavigatable(navigatable);
    myNotificationManager.showNotification(GradleConstants.SYSTEM_ID, notification);
  }

  public void activateView() {
    myNotificationManager.openMessageView(GradleConstants.SYSTEM_ID, NotificationSource.PROJECT_SYNC);
  }

  public boolean isEmpty() {
    return myNotificationManager.getMessageCount(NotificationSource.PROJECT_SYNC, null, GradleConstants.SYSTEM_ID) == 0;
  }
}
