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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.GradleUtil.isUsingExperimentalPlugin;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY;

public class LinkExternalCppProjectAction extends AndroidStudioGradleAction {

  public LinkExternalCppProjectAction() {
    super("Link External C++ Project", "Link an external C/C++ project (cmake or ndk-build) with this module", null);
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    DataContext dataContext = e.getDataContext();
    boolean enable = isValidAndroidGradleModuleSelected(dataContext);

    Presentation presentation = e.getPresentation();
    presentation.setEnabled(enable);
    presentation.setVisible(enable);
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

    AndroidGradleModel androidModel = AndroidGradleModel.get(module);
    if (androidModel == null || !androidModel.supportsExternalNativeBuild()) {
      return false;
    }

    if (isUsingExperimentalPlugin(module)) {
      return false; // Updating experimental plugin DSL is not yet supported.
    }


    NativeAndroidGradleModel nativeAndroidModel = NativeAndroidGradleModel.get(module);
    if (nativeAndroidModel != null) {
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
