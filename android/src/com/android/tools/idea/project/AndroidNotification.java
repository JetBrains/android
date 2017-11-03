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
package com.android.tools.idea.project;

import com.android.tools.idea.gradle.project.sync.hyperlink.CustomNotificationListener;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
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
 * Creates balloon notifications related to Android projects.
 */
public class AndroidNotification {
  public static final NotificationGroup BALLOON_GROUP = NotificationGroup.balloonGroup("Android Notification Group");
  public static final NotificationGroup LOG_ONLY_GROUP = NotificationGroup.logOnlyGroup("Android Notification Log-Only Group");

  @Nullable private Notification myNotification;
  @NotNull private final Project myProject;

  @NotNull
  public static AndroidNotification getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidNotification.class);
  }

  public AndroidNotification(@NotNull Project project) {
    myProject = project;
  }

  public void addLogEvent(@NotNull String title, @NotNull String text, @NotNull NotificationType type) {
    showNotification(title, text, type, LOG_ONLY_GROUP, null);
  }

  public void showBalloon(@NotNull String title, @NotNull String text, @NotNull NotificationType type) {
    showBalloon(title, text, type, (NotificationListener)null);
  }

  public void showBalloon(@NotNull String title,
                          @NotNull String text,
                          @NotNull NotificationType type,
                          @NotNull NotificationHyperlink... hyperlinks) {
    showBalloon(title, text, type, BALLOON_GROUP, hyperlinks);
  }

  public void showBalloon(@NotNull String title,
                          @NotNull String text,
                          @NotNull NotificationType type,
                          @NotNull NotificationGroup group,
                          @NotNull NotificationHyperlink... hyperlinks) {
    NotificationListener notificationListener = new CustomNotificationListener(myProject, hyperlinks);
    String newText = addHyperlinksToText(text, hyperlinks);
    showNotification(title, newText, type, group, notificationListener);
  }

  @NotNull
  private static String addHyperlinksToText(@NotNull String text, @NotNull NotificationHyperlink... hyperlinks) {
    // We need both "<br>" and "\n" to separate lines. IDEA will show this message in a balloon (which respects "<br>", and in the
    // 'Event Log' tool window, which respects "\n".)
    if (hyperlinks.length == 0) {
      return text;
    }
    StringBuilder b = new StringBuilder();
    b.append(text);

    for (NotificationHyperlink hyperlink : hyperlinks) {
      b.append("<br>\n").append(hyperlink.toHtml());
    }

    return b.toString();
  }

  public void showBalloon(@NotNull String title,
                          @NotNull String text,
                          @NotNull NotificationType type,
                          @Nullable NotificationListener listener) {
    showNotification(title, text, type, BALLOON_GROUP, listener);
  }

  private void showNotification(@NotNull String title,
                                @NotNull String text,
                                @NotNull NotificationType type,
                                @NotNull NotificationGroup group,
                                @Nullable NotificationListener listener) {
    Notification notification = group.createNotification(title, text, type, listener);
    Runnable notificationTask = () -> {
      if (!myProject.isDisposed() && myProject.isOpen()) {
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
