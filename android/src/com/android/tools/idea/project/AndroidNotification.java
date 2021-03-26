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

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.ui.CustomNotificationListener;
import com.google.common.base.Objects;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Key;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.awt.RelativePoint;
import java.awt.*;
import javax.swing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates balloon notifications related to Android projects.
 */
public class AndroidNotification {
  public static final NotificationGroup BALLOON_GROUP =
    NotificationGroup.balloonGroup("Android Notification Group", PluginId.getId("org.jetbrains.android"));
  public static final NotificationGroup LOG_ONLY_GROUP =
    NotificationGroup.logOnlyGroup("Android Notification Log-Only Group", PluginId.getId("org.jetbrains.android"));
  /**
   * Fallback destination to show a notification balloon if the project is not opened (hence no IDE frame).
   */
  private static final Key<JFrame> NOTIFICATION_DESTINATION_FALLBACK_KEY = Key.create("NOTIFICATION_DESTINATION_FALLBACK");

  @Nullable private Notification myNotification;
  @NotNull private final Project myProject;

  @NotNull
  public static AndroidNotification getInstance(@NotNull Project project) {
    return project.getService(AndroidNotification.class);
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
    showBalloon(title, text, type, group, true, hyperlinks);
  }

  public void showBalloon(@NotNull String title,
                          @NotNull String text,
                          @NotNull NotificationType type,
                          @NotNull NotificationGroup group,
                          boolean newLineForLinkText,
                          @NotNull NotificationHyperlink... hyperlinks) {
    NotificationListener notificationListener = new CustomNotificationListener(myProject, hyperlinks);
    String newText = addHyperlinksToText(text, newLineForLinkText, hyperlinks);
    showNotification(title, newText, type, group, notificationListener);
  }

  @NotNull
  private static String addHyperlinksToText(@NotNull String text,
                                            boolean newLineForLinkText,
                                            @NotNull NotificationHyperlink... hyperlinks) {
    // We need both "<br>" and "\n" to separate lines. IDEA will show this message in a balloon (which respects "<br>", and in the
    // 'Event Log' tool window, which respects "\n".)
    if (hyperlinks.length == 0) {
      return text;
    }
    StringBuilder b = new StringBuilder();
    b.append(text);

    for (NotificationHyperlink hyperlink : hyperlinks) {
      if (newLineForLinkText) {
        b.append("<br>\n");
      }
      b.append(hyperlink.toHtml());
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
      if (myProject.isDisposed()) {
        return;
      }
      if (myProject.isOpen()) {
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
      else {
        JFrame jFrame = getFallbackNotificationDestination(myProject);
        if (jFrame == null) {
          return;
        }
        showNotification(myProject, notification, jFrame);
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

  /**
   * Sets the fallback destination for notifications in a given project if the project is not opened.
   * <p>
   * With the IntelliJ platform, a notification is typically only displayed when the project is opened. In such case, the notification is
   * rendered in the {@link com.intellij.openapi.wm.IdeFrame} with that project opened. But for some use cases, for example
   * (go/project-aplos), such IDE frame never exists. In such situation, caller can set the destination JFrame of notifications with this
   * method when setting up the project.
   */
  public static void setFallbackNotificationDestination(Project project, JFrame jFrame) {
    project.putUserData(NOTIFICATION_DESTINATION_FALLBACK_KEY, jFrame);
    Notifications subscriber = new Notifications() {
      @Override
      public void notify(@NotNull Notification notification) {
        showNotification(project, notification, jFrame);
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(Notifications.TOPIC, subscriber);
    project.getMessageBus().connect().subscribe(Notifications.TOPIC, subscriber);
  }

  private static void showNotification(Project project, Notification notification, JFrame jFrame) {
    Balloon balloon =
      NotificationsManagerImpl
        .createBalloon(jFrame.getRootPane(), notification, false, true, BalloonLayoutData.fullContent(), project);

    Dimension jFrameSize = jFrame.getSize();
    Dimension balloonSize = balloon.getPreferredSize();

    // bottom-right corner
    RelativePoint
      point =
      new RelativePoint(jFrame, new Point(jFrameSize.width - balloonSize.width / 2, jFrameSize.height - balloonSize.height / 2));
    balloon.show(point, Balloon.Position.above);
  }

  @Nullable
  private static JFrame getFallbackNotificationDestination(Project project) {
    return project.getUserData(NOTIFICATION_DESTINATION_FALLBACK_KEY);
  }

  @Nullable
  public Notification getNotification() {
    return myNotification;
  }
}
