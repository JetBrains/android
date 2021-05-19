/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.run.OutputBuildActionUtil;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildBundleAction extends DumbAwareAction {
  private static final String ACTION_TEXT = "Build Bundle(s)";

  public BuildBundleAction() {
    super(ACTION_TEXT);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean enabled = StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.get() &&
                      isProjectBuildWithGradle(project);
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (isProjectBuildWithGradle(project)) {
      List<Module> appModules = DynamicAppUtils.getModulesSupportingBundleTask(project);
      if (!appModules.isEmpty()) {
        GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);
        gradleBuildInvoker.add(new GoToBundleLocationTask(project, appModules, ACTION_TEXT));
        Module[] modulesToBuild = appModules.toArray(Module.EMPTY_ARRAY);
        gradleBuildInvoker.bundle(modulesToBuild);
      }
      else {
        DynamicAppUtils.promptUserForGradleUpdate(project);
      }
    }
  }

  private static boolean isProjectBuildWithGradle(@Nullable Project project) {
    return project != null &&
           GradleProjectInfo.getInstance(project).isBuildWithGradle();
  }
}
