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
package com.android.tools.idea.model;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.DOT_CLASS;

/**
 * Build-system abstraction for finding class files from build output, or external library jar files.
 * Searches the scope of a build system as needed.
 */
public abstract class ClassJarProvider {

  @Nullable
  public abstract VirtualFile findModuleClassFile(@NotNull String className, @NotNull Module module);

  @Nullable
  public static VirtualFile findClassFileInPath(@NotNull VirtualFile parent, @NotNull String className) {
    if (!parent.exists()) {
      return null;
    }
    String[] relative = className.split("\\.");
    relative[relative.length - 1] = relative[relative.length - 1] + DOT_CLASS;
    VirtualFile file = VfsUtil.findRelativeFile(parent, relative);
    return (file != null && file.exists()) ? file : null;
  }

  @NotNull
  public abstract List<File> getModuleExternalLibraries(@NotNull Module module);
}
