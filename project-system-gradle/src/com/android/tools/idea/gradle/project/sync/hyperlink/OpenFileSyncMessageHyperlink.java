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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class OpenFileSyncMessageHyperlink extends SyncIssueNotificationHyperlink {
  private final @NotNull OpenFileHyperlink myOpenFileHyperlink;

  /**
   * Creates a file hyperlink. The line and column numbers should be 0-based. The file path should be a file system dependent path.
   */
  public OpenFileSyncMessageHyperlink(@NotNull String filePath, @NotNull String text, int lineNumber, int column) {
    super("openFile:" + filePath, text, AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK);
    myOpenFileHyperlink = new OpenFileHyperlink(filePath, text, lineNumber, column);
  }

  @Override
  protected void execute(@NotNull Project project) {
    myOpenFileHyperlink.execute(project);
  }

  @VisibleForTesting
  @NotNull
  public String getFilePath() {
    return myOpenFileHyperlink.getFilePath();
  }

  @VisibleForTesting
  public int getLineNumber() {
    return myOpenFileHyperlink.getLineNumber();
  }
}
