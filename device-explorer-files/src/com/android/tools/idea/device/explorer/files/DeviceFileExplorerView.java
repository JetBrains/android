/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files;

import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem;
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystemService;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public interface DeviceFileExplorerView {
  JComponent getComponent();
  void addListener(@NotNull DeviceExplorerViewListener listener);
  void removeListener(@NotNull DeviceExplorerViewListener listener);

  void setup();

  void showNoDeviceScreen();

  void reportErrorRelatedToDevice(@NotNull DeviceFileSystem fileSystem, @NotNull String message, @NotNull Throwable t);
  void reportErrorRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message, @NotNull Throwable t);

  void reportMessageRelatedToDevice(@NotNull DeviceFileSystem fileSystem, @NotNull String message);
  void reportMessageRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message);

  void startTreeBusyIndicator();
  void stopTreeBusyIndicator();

  void expandNode(@NotNull DeviceFileEntryNode treeNode);

  void addProgressListener(@NotNull DeviceExplorerViewProgressListener listener);
  void removeProgressListener(@NotNull DeviceExplorerViewProgressListener listener);
  void startProgress();
  void setProgressIndeterminate(boolean indeterminate);
  void setProgressValue(double fraction);
  void setProgressOkColor();
  void setProgressWarningColor();
  void setProgressErrorColor();
  void setProgressText(@NotNull String text);
  void stopProgress();
}
