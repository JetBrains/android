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

import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueEvent;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueFileEvent;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueQuickFix;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.project.hyperlink.SyncMessageFragment;
import com.android.tools.idea.ui.QuickFixNotificationListener;
import com.android.tools.idea.util.PositionInFile;
import com.intellij.build.SyncViewManager;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public abstract class AbstractSyncMessages implements Disposable {

  private Project myProject;

  @NotNull
  private final Object myLock = new Object();

  @GuardedBy("myLock")
  @NotNull
  private final List<SyncMessage> myCurrentMessages = new ArrayList<>();

  @NotNull private static final String PENDING_TASK_ID = "Pending taskId";

  protected AbstractSyncMessages(@NotNull Project project) {
    myProject = project;
  }

  /**
   * @return A string description containing sync issue groups, for example, "Unresolved dependencies".
   */
  @NotNull
  public String getErrorDescription() {
    Set<String> errorGroups = new LinkedHashSet<>();
    synchronized (myLock) {
      for (final var message : myCurrentMessages) {
        if (message.getType() == MessageType.ERROR) {
          errorGroups.add(message.getGroup());
        }
      }
    }
    return String.join(", ", errorGroups);
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myCurrentMessages.isEmpty();
    }
  }

  public void removeAllMessages() {
    synchronized (myLock) {
      myCurrentMessages.clear();
    }
  }

  public void report(@NotNull SyncMessage message) {
    String title = message.getGroup();
    String text = message.getText();
    NotificationCategory category = message.getType().convertToCategory();
    PositionInFile position = message.getPosition();

    NotificationData notification = createNotification(title, text, category, position);

    Navigatable navigatable = message.getNavigatable();
    notification.setNavigatable(navigatable);

    final var quickFixes = message.getQuickFixes();
    if (!quickFixes.isEmpty()) {
      updateNotification(notification, text, quickFixes);
    }

    // Save on array to be shown by build view later.
    Object taskId = GradleSyncState.getInstance(myProject).getExternalSystemTaskId();
    if (taskId == null) {
      taskId = PENDING_TASK_ID;
    }
    else {
      showNotification(notification, taskId, ContainerUtil.flatMap(quickFixes, AndroidSyncIssueQuickFix::create));
    }
    synchronized (myLock) {
      myCurrentMessages.add(message);
    }
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

  private void updateNotification(@NotNull NotificationData notification,
                                 @NotNull String text,
                                 @NotNull List<? extends SyncMessageFragment> quickFixes) {
    String message = text;
    StringBuilder b = new StringBuilder();
    for (final var handler : quickFixes) {
      String html = handler.toHtml();
      if (html.isEmpty()) continue;
      if (b.length() > 0) {
        b.append("\n");
      }
      b.append(html);
    }
    if (b.length() > 0) {
      message += ("\n" + b.toString());
    }
    notification.setMessage(message);

    addNotificationListener(notification, quickFixes);
  }

  // Call this method only if notification contains detailed text message with hyperlinks
  // Use updateNotification otherwise
  private void addNotificationListener(@NotNull NotificationData notification, @NotNull List<? extends SyncMessageFragment> quickFixes) {
    for (final var quickFix : quickFixes) {
      for (String url : quickFix.getUrls()) {
        notification.setListener(url, new QuickFixNotificationListener(myProject, quickFix));
      }
    }
  }

  private void showNotification(@NotNull NotificationData notification,
                                @NotNull Object taskId,
                                @NotNull List<? extends BuildIssueQuickFix> quickFixes) {
    String title = notification.getTitle();
    // Since the title of the notification data is the group, it is better to display the first line of the message
    String[] lines = notification.getMessage().split(System.lineSeparator());
    if (lines.length > 0) {
      title = lines[0];
    }

    AndroidSyncIssueEvent issueEvent;
    if (notification.getFilePath() != null) {
      issueEvent = new AndroidSyncIssueFileEvent(taskId, notification, title, quickFixes);
    }
    else {
      issueEvent = new AndroidSyncIssueEvent(taskId, notification, title, quickFixes);
    }

    myProject.getService(SyncViewManager.class).onEvent(taskId, issueEvent);
  }

  @NotNull
  protected abstract ProjectSystemId getProjectSystemId();

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  @TestOnly
  public @NotNull List<SyncMessage> getReportedMessages() {
    synchronized (myLock) {
      return new ArrayList<>(myCurrentMessages);
    }
  }

  @Override
  public void dispose() {
    myProject = null;
  }
}
