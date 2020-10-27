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

import com.intellij.build.FilePosition;
import com.intellij.build.events.FileMessageEvent;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSyncIssueFileEvent extends AndroidSyncIssueEvent implements FileMessageEvent {
  @NotNull private final AndroidSyncIssueFileEventResult myResult;

  public AndroidSyncIssueFileEvent(@NotNull Object parentId, @NotNull NotificationData notificationData, @NotNull String title,
                                   @NotNull List<? extends BuildIssueQuickFix> fixes) {
    super(parentId, notificationData, title, fixes);
    myResult = new AndroidSyncIssueFileEventResult(notificationData);
  }

  @NotNull
  @Override
  public AndroidSyncIssueFileEventResult getResult() {
    return myResult;
  }

  @Nullable
  @Override
  public FilePosition getFilePosition() {
    return myResult.getFilePosition();
  }
}
