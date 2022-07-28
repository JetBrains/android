/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolverKeys;
import com.android.tools.idea.gradle.util.GradleProjects;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Syncs project with Gradle, with an additional argument to refresh the linked C++ projects.
 */
public class RefreshLinkedCppProjectsAction extends SyncProjectAction {

  public RefreshLinkedCppProjectsAction() {
    super("Refresh Linked C++ Projects");
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    // Set this to true so that the request sent to gradle daemon contains arg -Pandroid.injected.refresh.external.native.model=true, which
    // would refresh the C++ project. See com.android.tools.idea.gradle.project.sync.common.CommandLineArgs for related logic.
    project.putUserData(AndroidGradleProjectResolverKeys.REFRESH_EXTERNAL_NATIVE_MODELS_KEY, true);
    super.doPerform(e, project);
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    if (containsExternalCppProjects(project)) {
      super.doUpdate(e, project);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  /** Checks if the given project contains a module that contains code built by Android Studio's C++ support. */
  private static boolean containsExternalCppProjects(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
      if (ndkModuleModel != null) {
        return true;
      }
    }
    return false;
  }
}
