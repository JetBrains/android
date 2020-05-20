/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.events;

import com.intellij.build.events.BuildIssueEvent;
import com.intellij.build.events.MessageEventResult;
import com.intellij.build.events.impl.AbstractBuildEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import java.util.List;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSyncIssueEvent extends AbstractBuildEvent implements BuildIssueEvent {

  @NotNull private final Kind myKind;
  @NotNull private final String myGroup;
  @NotNull private final AndroidSyncIssueEventResult myResult;
  @NotNull private final BuildIssue myBuildIssue;
  @Nullable private final Navigatable myNavigatable;

  public AndroidSyncIssueEvent(@NotNull Object parentId, @NotNull NotificationData notificationData, @NotNull String title,
                               @NotNull List<? extends BuildIssueQuickFix> fixes) {
    super(new Object(), parentId, System.currentTimeMillis(), title);
    myKind = convertCategory(notificationData.getNotificationCategory());
    myNavigatable = notificationData.getNavigatable();
    myResult = new AndroidSyncIssueEventResult(notificationData);
    myGroup = notificationData.getTitle();
    myBuildIssue = new AndroidSyncIssue(title, notificationData, fixes);
  }

  @Override
  public @NotNull BuildIssue getIssue() {
    return myBuildIssue;
  }

  @Contract(pure = true)
  @NotNull
  public static Kind convertCategory(@NotNull NotificationCategory category) {
    switch (category) {
      case SIMPLE:
        return Kind.SIMPLE;
      case INFO:
        return Kind.INFO;
      case ERROR:
        return Kind.ERROR;
      case WARNING:
        return Kind.WARNING;
      default:
        return Kind.ERROR;
    }
  }

  @NotNull
  @Override
  public Kind getKind() {
    return myKind;
  }

  @NotNull
  @Override
  public String getGroup() {
    return myGroup;
  }

  @Nullable
  @Override
  public Navigatable getNavigatable(@NotNull Project project) {
    return myNavigatable;
  }

  @NotNull
  @Override
  public MessageEventResult getResult() {
    return myResult;
  }
}
