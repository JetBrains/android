/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilities for working with test projects when testing the IDE.
 *
 * @see com.android.utils.FileUtils
 */
public final class TestFileUtils {
  private TestFileUtils() {
  }

  /**
   * Creates the directory if it doesn't exist and refreshes the VFS.
   */
  public static VirtualFile createDirectoriesAndRefreshVfs(@NotNull Path path) throws IOException {
    Files.createDirectories(path);
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile());
  }

  /**
   * Creates the file if it doesn't exist, writes the string to it, and refreshes the VFS.
   */
  public static VirtualFile writeFileAndRefreshVfs(@NotNull Path path, @NotNull String string) throws IOException {
    Files.createDirectories(path.getParent());
    Files.write(path, string.getBytes(StandardCharsets.UTF_8));

    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile());
  }
}
