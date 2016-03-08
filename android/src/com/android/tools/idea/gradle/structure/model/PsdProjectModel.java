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
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

public class PsdProjectModel extends PsdModel {
  @NotNull private final Project myProject;

  @NotNull private final List<PsdModuleModel> myModuleModels = Lists.newArrayList();

  private boolean myModified;

  public PsdProjectModel(@NotNull Project project) {
    super(null);
    myProject = project;

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      String gradlePath = getGradlePath(module);
      if (gradlePath != null) {
        // Only Gradle-based modules are displayed in the PSD.
        PsdModuleModel moduleModel = null;

        AndroidGradleModel gradleModel = AndroidGradleModel.get(module);
        if (gradleModel != null) {
          moduleModel = new PsdAndroidModuleModel(this, module, gradlePath, gradleModel);
        }
        if (moduleModel != null) {
          myModuleModels.add(moduleModel);
        }
      }
    }
  }

  @Nullable
  public PsdModuleModel findModelForModule(@NotNull String moduleName) {
    for (PsdModuleModel model : myModuleModels) {
      if (moduleName.equals(model.getName())) {
        return model;
      }
    }
    return null;
  }

  @Nullable
  public PsdModuleModel findModelByGradlePath(@NotNull String gradlePath) {
    for (PsdModuleModel model : myModuleModels) {
      if (gradlePath.equals(model.getGradlePath())) {
        return model;
      }
    }
    return null;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public String getName() {
    return myProject.getName();
  }

  @NotNull
  public List<PsdModuleModel> getModuleModels() {
    return myModuleModels;
  }

  @Override
  @Nullable
  public PsdModel getParent() {
    return null;
  }

  @Override
  public boolean isEditable() {
    return true;
  }

  @Override
  public boolean isModified() {
    return myModified;
  }

  @Override
  public void setModified(boolean value) {
    myModified = value;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return null;
  }
}
