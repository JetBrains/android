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

import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Manage files synchronized between devices and the local file system
 */
public interface DeviceExplorerFileManager {
  /**
   * Returns the default {@link Path} where to store the {@code entry} on the local file system.
   */
  @NotNull
  Path getDefaultLocalPathForEntry(@NotNull DeviceFileEntry entry);

  /**
   * Download asynchronously the content of a {@link DeviceFileEntry} onto the local file system.
   * Returns a {@link ListenableFuture} that completes when the download has completed.
   * The <code>progress</code> callback is regularly notified of the current progress of the
   * download operation.
   */
  @NotNull
  ListenableFuture<Void> downloadFileEntry(@NotNull DeviceFileEntry entry, @NotNull Path localPath, @NotNull FileTransferProgress progress);

  /**
   * Opens a previously downloaded file in an editor window. If the file contents is
   * not recognized, the implementation may open a dialog box asking the user to pick
   * the best editor type.
   *
   * <ul>
   * <li>Completes with a {@link RuntimeException} if the file can not be opened.</li>
   * <li>Completes with a {@link java.util.concurrent.CancellationException} if the user cancels
   * the 'choose editor type' dialog.</li>
   * </ul>
   */
  @NotNull
  ListenableFuture<Void> openFileInEditor(@NotNull Path localPath, boolean focusEditor);
}
