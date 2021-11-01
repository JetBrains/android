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
package com.android.tools.idea.navigator.nodes.apk;

import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public final class SourceFolders {
  private SourceFolders() {
  }

  public static boolean isInSourceFolder(@NotNull VirtualFile file, @NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      for (VirtualFile contentRoot : rootManager.getContentRoots()) {
        if (isAncestor(contentRoot, file, false /* not strict */)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isInSourceFolder(@NotNull VirtualFile file, @NotNull NativeLibrary library) {
    File filePath = virtualToIoFile(file);
    for (String path : library.getSourceFolderPaths()) {
      if (isAncestor(path, filePath.getPath(), false /* not strict */)) {
        return true;
      }
    }

    return false;
  }
}
