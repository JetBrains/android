/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.testartifact;

import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Search scope to check if a file belong to specific file roots (could be jar file or source root).
 * The implementation strategy is similar to {@link com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope}, which
 * uses {@code ProjectFileIndex} to locate the file root of a file and then check if the root is included by this scope.
 */
public class FileRootSearchScope extends GlobalSearchScope {
  private final TObjectIntHashMap<VirtualFile> myRoots = new TObjectIntHashMap<VirtualFile>();
  private final ProjectFileIndex myProjectFileIndex;

  public FileRootSearchScope(@NotNull Project project, @NotNull Set<VirtualFile> roots) {
    super(project);

    int i = 1;
    for (VirtualFile root : roots) {
      myRoots.put(root, i++);
    }
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (myProjectFileIndex.isInContent(file)) {
      return myRoots.contains(myProjectFileIndex.getSourceRootForFile(file));
    }
    return myRoots.contains(myProjectFileIndex.getClassRootForFile(file));
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    VirtualFile r1 = getFileRoot(file1);
    VirtualFile r2 = getFileRoot(file2);
    if (Comparing.equal(r1, r2)) {
      return 0;
    }

    if (r1 == null) {
      return -1;
    }
    if (r2 == null) {
      return 1;
    }

    int i1 = myRoots.get(r1);
    int i2 = myRoots.get(r2);
    if (i1 == 0 && i2 == 0) {
      return 0;
    }
    if (i1 > 0 && i2 > 0) {
      return i2 - i1;
    }
    return i1 > 0 ? 1 : -1;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }

  @Nullable
  private VirtualFile getFileRoot(@NotNull VirtualFile file) {
    if (myProjectFileIndex.isInContent(file)) {
      return myProjectFileIndex.getSourceRootForFile(file);
    }
    return myProjectFileIndex.getClassRootForFile(file);
  }

  @NotNull
  @Override
  public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (scope == this) return scope;

    if (scope instanceof FileRootSearchScope) {
      final Set<VirtualFile> roots = Sets.newHashSet();
      myRoots.forEach(new TObjectProcedure<VirtualFile>() {
        @Override
        public boolean execute(VirtualFile file) {
          roots.add(file);
          return true;
        }
      });

      ((FileRootSearchScope)scope).myRoots.forEach(new TObjectProcedure<VirtualFile>() {
        @Override
        public boolean execute(VirtualFile file) {
          roots.add(file);
          return true;
        }
      });

      return new FileRootSearchScope(getProject(), roots);
    }
    return super.uniteWith(scope);
  }
}
