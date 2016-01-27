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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

public class PsdProjectEditor {
  @NotNull private final Project myProject;

  @NotNull private final List<PsdModuleEditor> myModuleEditors = Lists.newArrayList();

  public PsdProjectEditor(@NotNull Project project) {
    myProject = project;

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      String gradlePath = getGradlePath(module);
      if (gradlePath != null) {
        // Only Gradle-based modules are displayed in the PSD.
        PsdModuleEditor editor = null;

        GradleBuildModel parsedModel = GradleBuildModel.get(module);
        if (parsedModel != null) {
          AndroidGradleModel gradleModel = AndroidGradleModel.get(module);
          if (gradleModel != null) {
            editor = new PsdAndroidModuleEditor(module, gradlePath, parsedModel, gradleModel, this);
          }
        }
        if (editor != null) {
          myModuleEditors.add(editor);
        }
      }
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public List<PsdModuleEditor> getModuleEditors() {
    return myModuleEditors;
  }
}
