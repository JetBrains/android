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
   * Download asynchronously the content of a {@link DeviceFileEntry} onto the local file system.
   * and returns a {@link ListenableFuture} the contains the local {@link Path} of the downloaded
   * file once the download is completed. The <code>progress</code> callback is regularly notified
   * of the current progress of the download operation.
   */
  @NotNull
  ListenableFuture<Path> downloadFileEntry(@NotNull DeviceFileEntry entry, @NotNull FileTransferProgress progress);

  /**
   * Opens a previously downloaded file in an editor window.
   *
   * The current implementation deletes the local file after the editor
   * windows is closed, or if no editor window can be opened. This
   * behavior will be changed when a better synchronization mechanism
   * is put in place.
   */
  void openFileInEditor(@NotNull Path localPath, boolean focusEditor);
}
