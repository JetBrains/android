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
package com.android.tools.idea.project.messages;

import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueEvent;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueFileEvent;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.ui.QuickFixNotificationListener;
import com.android.tools.idea.util.PositionInFile;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.MessageEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.EXTERNAL_SYSTEM_TASK_ID_KEY;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public abstract class AbstractSyncMessages {
  private static final NotificationSource NOTIFICATION_SOURCE = PROJECT_SYNC;

  @NotNull private final Project myProject;
  @NotNull private final List<AndroidSyncIssueEvent> myCurrentEvents = new ArrayList<>();

  protected AbstractSyncMessages(@NotNull Project project) {
    myProject = project;
    myCurrentEvents.clear();
  }

  public int getErrorCount() {
    int total = 0;
    for (AndroidSyncIssueEvent event : myCurrentEvents) {
      if (event.getKind() == MessageEvent.Kind.ERROR) {
        total++;
      }
    }
    return total;
  }

  public int getMessageCount(@NotNull String groupName) {
    int total = 0;
    for (AndroidSyncIssueEvent event : myCurrentEvents) {
      if (event.getGroup() == groupName) {
        total++;
      }
    }
    return total;
  }

  public boolean isEmpty() {
    return myCurrentEvents.isEmpty();
  }

  public void removeAllMessages() {
    clearEvents();
  }

  public void removeMessages(@NotNull String... groupNames) {
    Set<String> groupSet = new HashSet<>(Arrays.asList(groupNames));
    myCurrentEvents.removeIf(event -> groupSet.contains(event.getGroup()));
  }

  public void report(@NotNull SyncMessage message) {
    String title = message.getGroup();
    String text = join(message.getText(), "\n");
    NotificationCategory category = message.getType().convertToCategory();
    PositionInFile position = message.getPosition();

    NotificationData notification = createNotification(title, text, category, position);

    Navigatable navigatable = message.getNavigatable();
    notification.setNavigatable(navigatable);

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    if (!quickFixes.isEmpty()) {
      updateNotification(notification, text, quickFixes);
    }

    report(notification);
  }

  @NotNull
  public NotificationData createNotification(@NotNull String title,
                                             @NotNull String text,
                                             @NotNull NotificationCategory category,
                                             @Nullable PositionInFile position) {
    NotificationSource source = PROJECT_SYNC;
    if (position != null) {
      String filePath = virtualToIoFile(position.file).getPath();
      return new NotificationData(title, text, category, source, filePath, position.line, position.column, false);
    }
    return new NotificationData(title, text, category, source);
  }

  public void updateNotification(@NotNull NotificationData notification,
                                 @NotNull String text,
                                 @NotNull List<NotificationHyperlink> quickFixes) {
    String message = text;
    int hyperlinkCount = quickFixes.size();
    if (hyperlinkCount > 0) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < hyperlinkCount; i++) {
        b.append(quickFixes.get(i).toHtml());
        if (i < hyperlinkCount - 1) {
          b.append("<br>");
        }
      }
      message += ('\n' + b.toString());
    }
    notification.setMessage(message);

    addNotificationListener(notification, quickFixes);
  }

  // Call this method only if notification contains detailed text message with hyperlinks
  // Use updateNotification otherwise
  public void addNotificationListener(@NotNull NotificationData notification, @NotNull List<NotificationHyperlink> quickFixes) {
    for (NotificationHyperlink quickFix : quickFixes) {
      notification.setListener(quickFix.getUrl(), new QuickFixNotificationListener(myProject, quickFix));
    }
  }

  public void report(@NotNull NotificationData notificationData) {
    ExternalSystemTaskId id = myProject.getUserData(EXTERNAL_SYSTEM_TASK_ID_KEY);
    if (id != null) {
      String title = notificationData.getTitle();
      // Since the title of the notification data is the grooup, it is better to display the first line of the message
      String[] lines = notificationData.getMessage().split(SystemProperties.getLineSeparator());
      if (lines.length > 0) {
        title = lines[0];
      }
      AndroidSyncIssueEvent issueEvent;
      if (notificationData.getFilePath() != null) {
        issueEvent = new AndroidSyncIssueFileEvent(id, notificationData, title);
      }
      else {
        issueEvent = new AndroidSyncIssueEvent(id, notificationData, title);
      }
      myCurrentEvents.add(issueEvent);
      ServiceManager.getService(myProject, SyncViewManager.class).onEvent(issueEvent);
    }
  }

  @NotNull
  protected abstract ProjectSystemId getProjectSystemId();

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  @NotNull
  public List<AndroidSyncIssueEvent> getEvents() {
    return myCurrentEvents;
  }

  protected void clearEvents() {
    myCurrentEvents.clear();
  }
}
