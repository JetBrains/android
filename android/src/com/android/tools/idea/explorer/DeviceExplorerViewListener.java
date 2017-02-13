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

import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DeviceExplorerViewListener {
  void deviceSelected(@Nullable DeviceFileSystem device);
  void treeNodeExpanding(@NotNull DeviceFileEntryNode treeNode);
  void openNodeInEditorInvoked(@NotNull DeviceFileEntryNode treeNode);
  void saveNodeAsInvoked(@NotNull DeviceFileEntryNode treeNode);
  void copyNodePathInvoked(@NotNull DeviceFileEntryNode treeNode);
}
