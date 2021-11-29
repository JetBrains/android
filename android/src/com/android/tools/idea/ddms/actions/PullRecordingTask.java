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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PullRecordingTask extends Task.Modal {
  private final String myRemotePath;
  private final String myLocalPath;
  private final Runnable myHandleSavedFileRunnable;
  private final IDevice myDevice;

  public PullRecordingTask(
    @Nullable Project project,
    @NotNull IDevice device,
    @NotNull String remotePath,
    @NotNull String localFilePath,
    Runnable handleSavedFileRunnable) {
    super(project, ScreenRecorderAction.TITLE, false);
    myDevice = device;
    myRemotePath = remotePath;
    myLocalPath = localFilePath;
    myHandleSavedFileRunnable = handleSavedFileRunnable;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      myDevice.pullFile(myRemotePath, myLocalPath);
      myDevice.removeRemotePackage(myRemotePath);
    }
    catch (Exception e) {
      ScreenRecorderAction.showError(myProject, "Unexpected error while copying video recording from device", e);
    }
  }

  @Override
  public void onSuccess() {
    assert myProject != null;
    myHandleSavedFileRunnable.run();
  }
}
