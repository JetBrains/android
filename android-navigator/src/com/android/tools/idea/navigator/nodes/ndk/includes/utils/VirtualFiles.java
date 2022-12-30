/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk.includes.utils;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for methods involving VirtualFiles.
 */
public final class VirtualFiles {

  /**
   * This method converts a collection of excludes to their equivalent VirtualFile. The result is guaranteed to be a non-null ImmutableList
   * of non-null VirtualFiles. Null means there is no corresponding VirtualFile and these are removed from the list.
   */
  @NotNull
  public static ImmutableList<VirtualFile> convertToVirtualFile(@Nullable Collection<String> names) {
    if (names != null) {
      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      return ImmutableList.copyOf(
        names.stream().map(exclude -> fileSystem.findFileByIoFile(new File(exclude))).filter(exclude -> exclude != null)
          .collect(Collectors.toList()));
    }
    else {
      return ImmutableList.of();
    }
  }

  /**
   * This method checks whether the given element is an ancestor of any of the given excluded files.
   */
  public static boolean isElementAncestorOfExclude(PsiFileSystemItem element, ImmutableList<VirtualFile> files) {
    for (VirtualFile excluded : files) {
      if (VfsUtilCore.isAncestor(excluded, element.getVirtualFile(), false)) {
        // This file or folder is in the set to be excluded.
        return true;
      }
    }
    return false;
  }
}
