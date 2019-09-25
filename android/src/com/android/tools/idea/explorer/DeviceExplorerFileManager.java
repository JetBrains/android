/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer;

import com.android.tools.idea.device.fs.DownloadProgress;
import com.android.tools.idea.device.fs.DownloadedFileData;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/**
 * Manage files synchronized between devices and the local file system
 */
public interface DeviceExplorerFileManager {
  @NotNull
  static DeviceExplorerFileManager getInstance(Project project) {
    return ServiceManager.getService(project, DeviceExplorerFileManager.class);
  }

  /**
   * Returns the default {@link Path} where to store the {@code entry} on the local file system.
   */
  @NotNull
  Path getDefaultLocalPathForEntry(@NotNull DeviceFileEntry entry);

  /**
   * Asynchronously downloads the content of a {@link DeviceFileEntry} to the local file system.
   *
   * <p>Returns a {@link ListenableFuture} that completes when the download has completed.
   * The <code>progress</code> callback is regularly notified of the current progress of the
   * download operation.
   */
  @NotNull
  ListenableFuture<DownloadedFileData> downloadFileEntry(@NotNull DeviceFileEntry entry,
                                                         @NotNull Path localPath,
                                                         @NotNull DownloadProgress progress);

  /**
   * Opens a previously downloaded file and gives focus to the open component.
   * If the file contents is not recognized,
   * the implementation may open a dialog box asking the user to pick the best editor type.
   *
   * <ul>
   * <li>Completes with a {@link RuntimeException} if the file can not be opened.</li>
   * <li>Completes with a {@link java.util.concurrent.CancellationException} if the user cancels
   * the 'choose editor type' dialog.</li>
   * </ul>
   */
  @NotNull
  ListenableFuture<Void> openFile(@NotNull DeviceFileEntry entry, @NotNull Path localPath);
}