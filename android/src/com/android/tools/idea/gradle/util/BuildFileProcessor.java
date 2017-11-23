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
package com.android.tools.idea.gradle.util;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.dsl.api.GradleBuildModel.parseBuildFile;
import static com.intellij.openapi.vfs.VfsUtil.processFileRecursivelyWithoutIgnored;

public class BuildFileProcessor {
  @NotNull
  public static BuildFileProcessor getInstance() {
    return ServiceManager.getService(BuildFileProcessor.class);
  }

  public void processRecursively(@NotNull Project project, @NotNull Processor<GradleBuildModel> processor) {
    ApplicationManager.getApplication().runReadAction(() -> {
      VirtualFile projectRootFolder = project.getBaseDir();
      if (projectRootFolder == null) {
        // Unlikely to happen: this is default project.
        return;
      }

      processFileRecursivelyWithoutIgnored(projectRootFolder, virtualFile -> {
        if (FN_BUILD_GRADLE.equals(virtualFile.getName())) {
          GradleBuildModel buildModel = parseBuildFile(virtualFile, project);
          return processor.process(buildModel);
        }
        return true;
      });
    });
  }
}
