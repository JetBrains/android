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

import static com.intellij.notification.NotificationType.ERROR;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
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

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
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
      List<Module> appModules = GradleProjectSystemUtil.getAppHolderModulesSupportingBundleTask(project);
      if (appModules.size() > 0) {
        GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);
        GoToBundleLocationTask task = new GoToBundleLocationTask(project, appModules, ACTION_TEXT);
        Module[] modulesToBuild = appModules.toArray(Module.EMPTY_ARRAY);
        task.executeWhenBuildFinished(gradleBuildInvoker.bundle(modulesToBuild));
      }
      else {
        AndroidNotification.getInstance(project).showBalloon(ACTION_TEXT, "No modules supporting bundles found", ERROR);
      }
    }
  }

  private static boolean isProjectBuildWithGradle(@Nullable Project project) {
    return project != null && ProjectSystemUtil.getProjectSystem(project) instanceof GradleProjectSystem;
  }
}
