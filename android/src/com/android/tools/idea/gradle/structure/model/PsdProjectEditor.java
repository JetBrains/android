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
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleEditor;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

public class PsdProjectEditor implements PsdModelEditor {
  @NotNull private final Project myProject;

  @NotNull private final List<PsdModuleEditor> myModuleEditors = Lists.newArrayList();

  private boolean myModified;

  public PsdProjectEditor(@NotNull Project project) {
    myProject = project;

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      String gradlePath = getGradlePath(module);
      if (gradlePath != null) {
        // Only Gradle-based modules are displayed in the PSD.
        PsdModuleEditor moduleEditor = null;

        AndroidGradleModel gradleModel = AndroidGradleModel.get(module);
        if (gradleModel != null) {
          moduleEditor = new PsdAndroidModuleEditor(this, module, gradlePath, gradleModel);
        }
        if (moduleEditor != null) {
          myModuleEditors.add(moduleEditor);
        }
      }
    }
  }

  @Nullable
  public PsdModuleEditor findEditorForModule(@NotNull String moduleName) {
    for (PsdModuleEditor editor : myModuleEditors) {
      if (moduleName.equals(editor.getModuleName())) {
        return editor;
      }
    }
    return null;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public List<PsdModuleEditor> getModuleEditors() {
    return myModuleEditors;
  }

  @Override
  @Nullable
  public PsdModelEditor getParent() {
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
}
