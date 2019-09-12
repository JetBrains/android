/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.intellij.openapi.ui.Messages.YES;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.ui.Messages.showYesNoDialog;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import org.jetbrains.annotations.NotNull;

public class DeleteFileAndSyncHyperlink extends NotificationHyperlink {
  @NotNull private File myFile;
  @NotNull private GradleSyncStats.Trigger myTrigger;

  public DeleteFileAndSyncHyperlink(@NotNull File file, @NotNull GradleSyncStats.Trigger trigger) {
    super("deleteFileAndSync", "Delete file and sync project");
    myFile = file;
    myTrigger = trigger;
  }

  @Override
  protected void execute(@NotNull Project project) {
    if (showYesNoDialog(project, "Are you sure you want to delete this file?\n\n" + myFile.getPath(), "Delete File", null) == YES) {
      if (FileUtil.delete(myFile)) {
        GradleSyncInvoker.getInstance().requestProjectSync(project, myTrigger);
      }
      else {
        showErrorDialog(project, "Could not delete " + myFile.getPath(), "Delete File");
      }
    }
  }

  @VisibleForTesting
  @NotNull
  public File getFile() {
    return myFile;
  }
}
