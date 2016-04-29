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
package com.android.tools.idea.gradle.testing;

import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Search scope to check if a file belong to specific file roots (could be jar file or source root).
 * The implementation strategy is similar to {@link com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope}, which
 * uses {@code ProjectFileIndex} to locate the file root of a file and then check if the root is included by this scope.
 */
public class FileRootSearchScope extends GlobalSearchScope {
  @NotNull private final TObjectIntHashMap<File> myDirRootPaths = new TObjectIntHashMap<File>();
  @NotNull private final ProjectFileIndex myProjectFileIndex;

  public FileRootSearchScope(@NotNull Project project, @NotNull Collection<File> rootDirPaths) {
    super(project);
    int i = 1;
    for (File root : rootDirPaths) {
      myDirRootPaths.put(root, i++);
    }
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  // This API is mostly for AndroidJunitPatcher and has linear time complexity, we can remove it after we run unit test through gradle
  /**
   * Check if a java.io.File is contained in the scope, slightly differently than {@link #accept(VirtualFile)} which calls
   * {@link #contains(VirtualFile)}, if the file does not exists, if will try to check any of its parents are in the scope.
   */
  public boolean accept(@NotNull File file) {
    while (!file.exists()) {
      if (myDirRootPaths.containsKey(file)) {
        return true;
      }
      file = file.getParentFile();
    }
    VirtualFile virtualFile = findFileByIoFile(file, true);
    if (virtualFile != null) {
      return accept(virtualFile);
    }
    return false;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    File path = virtualToIoFile(file);
    if (myDirRootPaths.contains(path)) {
      return true;
    }
    if (myProjectFileIndex.isInContent(file)) {
      VirtualFile sourceRootForFile = myProjectFileIndex.getSourceRootForFile(file);
      if (sourceRootForFile != null) {
        path = virtualToIoFile(sourceRootForFile);
        return myDirRootPaths.contains(path);
      }
    }
    VirtualFile classRootForFile = myProjectFileIndex.getClassRootForFile(file);
    if (classRootForFile != null) {
      path = virtualToIoFile(classRootForFile);
      return myDirRootPaths.contains(path);
    }
    return false;
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
    int i1 = myDirRootPaths.get(virtualToIoFile(r1));
    int i2 = myDirRootPaths.get(virtualToIoFile(r2));
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
    if (scope instanceof FileRootSearchScope) {
      return merge((FileRootSearchScope)scope);
    }
    return super.uniteWith(scope);
  }

  /**
   * Create a scope whose {@link #myDirRootPaths} is the merge of those two scopes. The scope created is equivalent to the ones created
   * using {@link GlobalSearchScope#uniteWith} or {@link SearchScope#union} but has better query time performance and worse creation time
   * performance. This method is only supposed to be used in {@link TestArtifactSearchScopes} where we create the base scopes.
   */
  @NotNull
  protected FileRootSearchScope merge(@NotNull FileRootSearchScope scope) {
    return calculate(scope, true);
  }

  @NotNull
  protected FileRootSearchScope exclude(@NotNull FileRootSearchScope scope) {
    return calculate(scope, false);
  }

  @NotNull
  private FileRootSearchScope calculate(@NotNull FileRootSearchScope scope, final boolean merge) {
    final Set<File> roots = Sets.newHashSet();
    myDirRootPaths.forEach(new TObjectProcedure<File>() {
      @Override
      public boolean execute(File file) {
        roots.add(file);
        return true;
      }
    });

    scope.myDirRootPaths.forEach(new TObjectProcedure<File>() {
      @Override
      public boolean execute(File file) {
        if (merge) {
          roots.add(file);
        } else {
          roots.remove(file);
        }
        return true;
      }
    });

    Project project = getProject();
    assert project != null;
    return new FileRootSearchScope(project, roots);
  }
}
