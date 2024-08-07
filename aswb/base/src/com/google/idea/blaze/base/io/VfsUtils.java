/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.io;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.annotation.Nullable;

/** A helper class */
public final class VfsUtils {

  private VfsUtils() {}

  /**
   * Attempts to resolve the given file path to a {@link VirtualFile}.
   *
   * <p>WARNING: Refreshing files on the EDT may freeze the IDE.
   *
   * @param refreshIfNeeded whether to refresh the file in the VFS, if it is not already cached.
   *     Will only refresh if called on the EDT.
   */
  @Nullable
  public static VirtualFile resolveVirtualFile(File file, boolean refreshIfNeeded) {
    LocalFileSystem fileSystem = VirtualFileSystemProvider.getInstance().getSystem();
    VirtualFile vf = fileSystem.findFileByPathIfCached(file.getPath());
    if (vf != null) {
      return vf;
    }
    vf = fileSystem.findFileByIoFile(file);
    if (vf != null && vf.isValid()) {
      return vf;
    }
    boolean shouldRefresh =
        refreshIfNeeded && ApplicationManager.getApplication().isDispatchThread();
    return shouldRefresh ? fileSystem.refreshAndFindFileByIoFile(file) : null;
  }
}
