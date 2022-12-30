/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.io;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.Nullable;

/**
 * File-related methods dependent on the IntelliJ Platform.
 */
public final class IdeFileUtils {
  /**
   * Returns the Desktop directory for the current user, or {@code null} if it doesn't exist.
   * Copied from {@link com.intellij.openapi.fileChooser.actions.GotoDesktopDirAction} with minor changes.
   */
  @Nullable
  public static Path getDesktopDirectory() {
    Path desktop = Paths.get(SystemProperties.getUserHome(), "Desktop");

    if (!Files.isDirectory(desktop) && SystemInfo.hasXdgOpen()) {
      String path = ExecUtil.execAndReadLine(new GeneralCommandLine("xdg-user-dir", "DESKTOP"));
      if (path != null) {
        desktop = Paths.get(path);
      }
    }

    return Files.isDirectory(desktop) ? desktop : null;
  }

  /**
   * Returns the Desktop directory for the current user, or null if it doesn't exist.
   */
  @Nullable
  public static VirtualFile getDesktopDirectoryVirtualFile() {
    Path desktop = getDesktopDirectory();
    return desktop == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByIoFile(desktop.toFile());
  }
}
