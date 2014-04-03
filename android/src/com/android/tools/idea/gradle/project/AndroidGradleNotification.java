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
package com.android.tools.idea.gradle.project;

import com.google.common.base.Objects;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates balloon notifications related to Gradle-based Android projects.
 */
public class AndroidGradleNotification {
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Android/Gradle Notification Group");

  @Nullable private Notification myNotification;
  @NotNull private final Project myProject;

  @NotNull
  public static AndroidGradleNotification getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidGradleNotification.class);
  }

  public AndroidGradleNotification(@NotNull Project project) {
    myProject = project;
  }

  public void showBalloon(@NotNull String title, @NotNull String message, @NotNull NotificationType type) {
    showBalloon(title, message, type, null);
  }


  public void showBalloon(@NotNull final String title,
                          @NotNull final String message,
                          @NotNull final NotificationType type,
                          @Nullable final NotificationListener listener) {
    showBalloon(title, message, type, NOTIFICATION_GROUP, listener);
  }

  public void showBalloon(@NotNull final String title,
                          @NotNull final String message,
                          @NotNull final NotificationType type,
                          @NotNull final NotificationGroup group,
                          @Nullable final NotificationListener listener) {
    Runnable notificationTask = new Runnable() {
      @Override
      public void run() {
        if (!myProject.isDisposed() && myProject.isOpen()) {
          Notification notification = group.createNotification(title, message, type, listener);
          Notification old = myNotification;
          if (old != null) {
            boolean similar = Objects.equal(notification.getContent(), old.getContent());
            if (similar) {
              old.expire();
            }
          }
          myNotification = notification;
          notification.notify(myProject);
        }
      }
    };
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      notificationTask.run();
    }
    else {
      application.invokeLater(notificationTask);
    }
  }

  @Nullable
  public Notification getNotification() {
    return myNotification;
  }
}
