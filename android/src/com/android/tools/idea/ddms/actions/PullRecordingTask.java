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
package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.IDevice;
import com.intellij.CommonBundle;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.fileTypes.NativeFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PullRecordingTask extends Task.Modal {
  private final String myLocalPath;
  private final IDevice myDevice;

  public PullRecordingTask(@Nullable Project project, @NotNull IDevice device, @NotNull String localFilePath) {
    super(project, ScreenRecorderAction.TITLE, false);
    myDevice = device;
    myLocalPath = localFilePath;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      myDevice.pullFile(ScreenRecorderAction.REMOTE_PATH, myLocalPath);
      myDevice.removeRemotePackage(ScreenRecorderAction.REMOTE_PATH);
    }
    catch (Exception e) {
      ScreenRecorderAction.showError(myProject, "Unexpected error while copying video recording from device", e);
    }
  }

  // Tries to open the file at myLocalPath
  private void openSavedFile() {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myLocalPath);
    if (file != null) {
      NativeFileType.openAssociatedApplication(file);
    }
  }

  @Override
  public void onSuccess() {
    assert myProject != null;

    String message = "Video Recording saved as " + myLocalPath;
    String cancel = CommonBundle.getOkButtonText();
    Icon icon = Messages.getInformationIcon();

    if (ShowFilePathAction.isSupported()) {
      String no = "Show in " + ShowFilePathAction.getFileManagerName();
      int exitCode = Messages.showYesNoCancelDialog(myProject, message, ScreenRecorderAction.TITLE, "Open", no, cancel, icon);

      if (exitCode == Messages.YES) {
        openSavedFile();
      }
      else if (exitCode == Messages.NO) {
        ShowFilePathAction.openFile(new File(myLocalPath));
      }
    }
    else if (Messages.showOkCancelDialog(myProject, message, ScreenRecorderAction.TITLE, "Open File", cancel, icon) == Messages.OK) {
      openSavedFile();
    }
  }
}
