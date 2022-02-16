/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle;

import com.android.tools.idea.project.ModuleBasedClassFileFinder;
import com.android.tools.idea.projectsystem.ClassFileFinderUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleClassFileFinder extends ModuleBasedClassFileFinder {
  private final boolean includeAndroidTests;

  public GradleClassFileFinder(@NotNull Module module) {
    this(module, false);
  }

  public GradleClassFileFinder(@NotNull Module module, boolean includeAndroidTests) {
    super(module);
    this.includeAndroidTests = includeAndroidTests;
  }

  @Override
  @Nullable
  protected VirtualFile findClassFileInModule(@NotNull Module module, @NotNull String className) {
    return GradleClassFinderUtil.getModuleCompileOutputs(module, includeAndroidTests)
      .map(file -> VfsUtil.findFileByIoFile(file, true))
      .filter(Objects::nonNull)
      .map(vFile -> ClassFileFinderUtil.findClassFileInOutputRoot(vFile, className))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }
}
