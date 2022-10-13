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

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.file.explorer.toolwindow.DeviceFileEntryNode;
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystem;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@UiThread
public interface DeviceExplorerViewListener {
  void noDeviceSelected();
  void deviceSelected(@NotNull DeviceFileSystem device);
  void treeNodeExpanding(@NotNull DeviceFileEntryNode treeNode);
  void openNodesInEditorInvoked(@NotNull List<DeviceFileEntryNode> treeNodes);
  void saveNodesAsInvoked(@NotNull List<DeviceFileEntryNode> treeNodes);
  void copyNodePathsInvoked(@NotNull List<DeviceFileEntryNode> treeNodes);
  void newDirectoryInvoked(@NotNull DeviceFileEntryNode parentTreeNode);
  void newFileInvoked(@NotNull DeviceFileEntryNode parentTreeNode);
  void deleteNodesInvoked(@NotNull List<DeviceFileEntryNode> nodes);
  void synchronizeNodesInvoked(@NotNull List<DeviceFileEntryNode> nodes);
  void uploadFilesInvoked(@NotNull DeviceFileEntryNode treeNode);
  void uploadFilesInvoked(@NotNull DeviceFileEntryNode treeNode, List<Path> files);
}
