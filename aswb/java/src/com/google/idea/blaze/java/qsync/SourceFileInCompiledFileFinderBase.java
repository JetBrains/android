/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.qsync;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.JarFileSystem;

/**
 * A base class used to find source files existed in source jar.
 */
abstract class SourceFileInCompiledFileFinderBase extends SourceFileFinderBase{
  public SourceFileInCompiledFileFinderBase(PsiFile clsFile) {
    super(clsFile);
  }

  @Override
  public VirtualFile convertToVirtualFile(Path path) {
    return JarFileSystem.getInstance().findLocalVirtualFileByPath(path.toString());
  }

  public Set<PsiFile> getMatchingPsiFile(VirtualFile vf) {
    Set<PsiFile> matchingPsiFiles = new HashSet<>();
    ContentIterator iterator =
      (VirtualFile fileOrDir) -> {
        if (!fileOrDir.isDirectory()) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(fileOrDir);
          if (psiFile != null && containsClass(psiFile)) {
            matchingPsiFiles.add(psiFile);
          }
        }
        return true;
      };
    VfsUtilCore.iterateChildrenRecursively(vf, null, iterator);
    return matchingPsiFiles;
  }
}
