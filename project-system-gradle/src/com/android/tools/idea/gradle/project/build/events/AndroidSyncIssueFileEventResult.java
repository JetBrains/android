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
import com.intellij.build.events.FileMessageEventResult;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class AndroidSyncIssueFileEventResult extends AndroidSyncIssueEventResult implements FileMessageEventResult {
  @NotNull private final FilePosition myFilePosition;

  public AndroidSyncIssueFileEventResult(@NotNull NotificationData notificationData) {
    super(notificationData);
    assert notificationData.getFilePath() != null;
    int line = notificationData.getLine() < 0 ? -1 : notificationData.getLine() - 1;
    int column = notificationData.getColumn() < 0 ? -1 : notificationData.getColumn() - 1;
    myFilePosition = new FilePosition(new File(notificationData.getFilePath()), line, column);
  }

  @Override
  @NotNull
  public FilePosition getFilePosition() {
    return myFilePosition;
  }
}
