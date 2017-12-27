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
package com.android.tools.idea.fileTypes;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;

/**
 * Icon provider for android Files
 */
public class AndroidFileIconProvider implements FileIconProvider {
  /**
   * Returns an icon used in Android files.
   * @param file File to look an icon for.
   * @param flags (not used)
   * @param project Project that this file belongs to.
   * @return An icon if file is root of a module, {@code null} otherwise.
   */
  @Nullable
  @Override
  public Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
    if (project != null) {
      ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      Module module = projectFileIndex.getModuleForFile(file);
      if (module != null) {
        VirtualFile moduleFile = module.getModuleFile();
        if (moduleFile != null && file.equals(moduleFile)) {
          return getModuleIcon(module);
        }
      }
    }
    return null;
  }
}
