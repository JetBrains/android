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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.util.Projects.findModuleRootFolderPath;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

class ApkPathFinder {
  @Nullable
  File findExistingApkPath(@NotNull Module module, @Nullable String potentialPathValue) {
    if (isNotEmpty(potentialPathValue)) {
      File potentialPath = new File(potentialPathValue);
      if (potentialPath.isDirectory()) {
        return potentialPath;
      }
    }
    if (GradleProjectInfo.getInstance(module.getProject()).isBuildWithGradle()) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
        File potentialPath = new File(buildFolderPath, join("outputs", "apk"));
        if (potentialPath.isDirectory()) {
          return potentialPath;
        }
        if (buildFolderPath.isDirectory()) {
          return buildFolderPath;
        }
      }
    }
    File potentialPath = findModuleRootFolderPath(module);
    return potentialPath != null && potentialPath.isDirectory() ? potentialPath : null;
  }
}
