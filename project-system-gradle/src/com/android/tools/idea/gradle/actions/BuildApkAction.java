/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class BuildApkAction extends DumbAwareAction {
  private static final String ACTION_TEXT = "Build APK(s)";

  public BuildApkAction() {
    super(ACTION_TEXT);
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null && ProjectSystemUtil.getProjectSystem(project) instanceof GradleProjectSystem);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && ProjectSystemUtil.getProjectSystem(project) instanceof GradleProjectSystem) {
      List<Module> appModules = ProjectStructure.getInstance(project).getAppHolderModules().stream()
        .flatMap(module -> GradleProjectSystemUtil.getModulesToBuild(module).stream())
        .collect(Collectors.toList());
      if (!appModules.isEmpty()) {
        GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);
        GoToApkLocationTask task = new GoToApkLocationTask(project, appModules, ACTION_TEXT);
        Module[] modulesToBuild = appModules.toArray(Module.EMPTY_ARRAY);
        task.executeWhenBuildFinished(gradleBuildInvoker.assemble(modulesToBuild));
      }
    }
  }
}
