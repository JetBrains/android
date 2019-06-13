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
package com.android.tools.idea.deviceExplorer;

import com.android.annotations.NonNull;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Interface used by {@link DeviceExplorerController} to handle files selected by the user.
 * This interface is used as an extension point to allow other modules to define specific behaviour for handling files.
 */
public interface FileHandler {
  ExtensionPointName<FileHandler> EP_NAME = ExtensionPointName.create("com.android.tools.idea.explorer.fileHandler");

  /**
   * Returns a list of device file paths that are logically related to the deviceFilePath passed as input.
   * @param deviceFilePath The path on the device of the file selected by the user.
   * @param localFile The file corresponding to "deviceFilePath", downloaded on local storage.
   * @return a list of additional paths on the device, that have to be downloaded.
   *    The list can be empty, if there are no additional files to handle.
   */
  @NonNull
  List<String> getAdditionalDevicePaths(@NotNull String deviceFilePath, @NotNull VirtualFile localFile);
}