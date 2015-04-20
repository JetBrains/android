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

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractSyncErrorHandler {

  public static final ExtensionPointName<AbstractSyncErrorHandler> EP_NAME
    = ExtensionPointName.create("com.android.gradle.syncErrorHandler");

  public static final String FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT = "Failed to sync Gradle project '%1$s'";

  protected static final NotificationHyperlink[] EMPTY = {};

  private static final Pattern ERROR_LOCATION_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)' line: ([\\d]+)");
  private static final Pattern ERROR_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)'");

  protected static final NotificationType DEFAULT_NOTIFICATION_TYPE = NotificationType.ERROR;

  /**
   * Attempts to handle a project sync error.
   *
   * @param message      the error message (from the given {@link ExternalSystemException},) separated by lines.
   * @param error        the project sync error.
   * @param notification this is what will be displayed in the "Messages" tool window.
   * @param project      the given project.
   * @return {@code true} if the project sync error was successfully handled; {@code false} otherwise.
   */
  public abstract boolean handleError(@NotNull List<String> message,
                                      @NotNull ExternalSystemException error,
                                      @NotNull NotificationData notification,
                                      @NotNull Project project);

  @Nullable
  protected static Pair<String, Integer> getErrorLocation(@NotNull String msg) {
    Matcher matcher = ERROR_LOCATION_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      int line = -1;
      try {
        line = Integer.parseInt(matcher.group(2));
      }
      catch (NumberFormatException e) {
        // ignored.
      }
      return Pair.create(filePath, line);
    }

    matcher = ERROR_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      return Pair.create(filePath, -1);
    }
    return null;
  }


  protected static void updateNotification(@NotNull NotificationData notification,
                                           @NotNull Project project,
                                           @NotNull String errorMsg,
                                           @NotNull List<NotificationHyperlink> hyperlinks) {
    updateNotification(notification, project, errorMsg, hyperlinks.toArray(new NotificationHyperlink[hyperlinks.size()]));
  }

  protected static void updateNotification(@NotNull NotificationData notification,
                                           @NotNull final Project project,
                                           @NotNull String errorMsg,
                                           @NotNull NotificationHyperlink... hyperlinks) {
    String title = String.format(FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT, project.getName());
    updateNotification(notification, project, title, errorMsg, hyperlinks);
  }

  public static void updateNotification(@NotNull NotificationData notification,
                                        @NotNull Project project,
                                        @NotNull String title,
                                        @NotNull String errorMsg,
                                        @NotNull NotificationHyperlink... hyperlinks) {
    String text = errorMsg;
    int hyperlinkCount = hyperlinks.length;
    if (hyperlinkCount > 0) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < hyperlinkCount; i++) {
        b.append(hyperlinks[i].toHtml());
        if (i < hyperlinkCount - 1) {
          b.append("<br>");
        }
      }
      text += ('\n' + b.toString());
    }

    notification.setTitle(title);
    notification.setMessage(text);
    addNotificationListener(notification, project, hyperlinks);
  }

  protected static void addNotificationListener(@NotNull NotificationData notification,
                                                @NotNull final Project project,
                                                @NotNull NotificationHyperlink... hyperlinks) {
    for (final NotificationHyperlink hyperlink : hyperlinks) {
      notification.setListener(hyperlink.getUrl(), new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
          hyperlink.executeIfClicked(project, e);
        }
      });
    }
  }
}
