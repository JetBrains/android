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
package com.android.tools.idea.navigator.nodes.apk.ndk;

import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Ensures that the "cpp" node only includes folders that are considered "source folders."
 */
public class SourceCodeFilter implements PsiFileSystemItemFilter {
  @NotNull private final List<String> mySourceFolderPaths;

  SourceCodeFilter(@NotNull List<String> sourceFolderPaths) {
    mySourceFolderPaths = sourceFolderPaths;
  }

  @Override
  public boolean shouldShow(@NotNull PsiFileSystemItem item) {
    if (item instanceof PsiDirectory) {
      PsiDirectory psiFolder = (PsiDirectory)item;
      VirtualFile folder = psiFolder.getVirtualFile();
      String folderPath = virtualToIoFile(folder).toString();
      return isValidPath(folderPath);
    }
    return true;
  }

  private boolean isValidPath(@NotNull String path) {
    for (String existing : mySourceFolderPaths) {
      if (existing.contains(path) /* 'path' is parent */ || path.contains(existing) /* 'path' is child */) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SourceCodeFilter)) {
      return false;
    }
    SourceCodeFilter filter = (SourceCodeFilter)o;
    return Objects.equals(mySourceFolderPaths, filter.mySourceFolderPaths);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mySourceFolderPaths);
  }
}
