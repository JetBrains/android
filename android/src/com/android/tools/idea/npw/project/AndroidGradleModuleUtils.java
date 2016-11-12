/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class AndroidGradleModuleUtils {

  /**
   * Given a file and a project, return the Module corresponding to the containing Gradle project for the file.  If the file is not
   * contained by any project then return null
   */
  @Nullable
  static public Module getContainingModule(File file, Project project) {
    VirtualFile vFile = VfsUtil.findFileByIoFile(file, false);
    if (vFile == null) {
      return null;
    }
    Module bestMatch = null;
    int bestMatchValue = Integer.MAX_VALUE;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      GradleFacet facet = GradleFacet.getInstance(module);
      if (facet != null) {
        GradleModuleModel gradleModuleModel = facet.getGradleModuleModel();
        assert gradleModuleModel != null;
        VirtualFile buildFile = gradleModuleModel.getBuildFile();
        if (buildFile != null) {
          VirtualFile root = buildFile.getParent();
          if (VfsUtilCore.isAncestor(root, vFile, true)) {
            String relativePath = VfsUtilCore.getRelativePath(vFile, root, '/');
            if (relativePath != null) {
              int value = Iterables.size(Splitter.on('/').split(relativePath));
              if (value < bestMatchValue) {
                bestMatch = module;
                bestMatchValue = value;
              }
            }
          }
        }
      }
    }
    return bestMatch;
  }


}
