/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY;

public class LinkExternalCppProjectAction extends AndroidStudioGradleAction {

  public LinkExternalCppProjectAction() {
    super("Link C++ Project with Gradle", "Link an external C/C++ project (cmake or ndk-build) with Gradle", null);
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    DataContext dataContext = e.getDataContext();
    boolean enable = isValidAndroidGradleModuleSelected(dataContext) && !StudioFlags.NPW_NEW_NATIVE_MODULE.get();

    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(enable);
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    DataContext dataContext = e.getDataContext();
    Module module = getSelectedModule(dataContext);
    assert module != null;

    new LinkExternalCppProjectDialog(module).show();
  }

  private static boolean isValidAndroidGradleModuleSelected(@NotNull DataContext dataContext) {
    Module module = getSelectedModule(dataContext);

    if(module == null) {
      return false;
    }

    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel == null || !androidModel.getFeatures().isExternalBuildSupported()) {
      return false;
    }

    NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
    if (ndkModuleModel != null) {
      return false; // Some external native project is already linked to this module.
    }

    if (GradleBuildModel.get(module) == null) {
      return false; // This should never for an fully synced module, but checking for just in case.
    }

    return true;
  }

  @Nullable
  private static Module getSelectedModule(@NotNull DataContext dataContext) {
    Module[] modules = MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modules != null) {
      if (modules.length == 1) {
        return modules[0];
      }
    }
    return MODULE.getData(dataContext);
  }
}
