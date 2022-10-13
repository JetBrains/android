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

import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystem;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DeviceExplorerModelListener {

  /**
   * A new device has been discovered, e.g. attached to a USB port.
   */
  void deviceAdded(@NotNull DeviceFileSystem device);

  /**
   * An existing device is not available anymore, e.g. disconnected
   * from the USB port.
   */
  void deviceRemoved(@NotNull DeviceFileSystem device);

  /**
   * The state of an existing device has changed, e.g. online -> offline.
   */
  void deviceUpdated(@NotNull DeviceFileSystem device);

  /**
   * The currently active device has changed.
   */
  void activeDeviceChanged(@Nullable DeviceFileSystem newActiveDevice);

  /**
   * The tree model of the file system has just changed, e.g. just after
   * the currently active device has changed.
   *
   * If there is no active device, <code>newTreeModel</code> is <code>null</code>.
   */
  void treeModelChanged(@Nullable DefaultTreeModel newTreeModel, @Nullable DefaultTreeSelectionModel newTreeSelectionModel);
}
