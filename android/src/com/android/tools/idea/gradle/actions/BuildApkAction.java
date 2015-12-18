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

import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.invoker.GradleInvoker.TestCompileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class BuildApkAction extends DumbAwareAction {
  public BuildApkAction() {
    super("Build APK");
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null && isBuildWithGradle(project));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && isBuildWithGradle(project)) {
      GoToApkLocationTask task = null;

      Module[] modules = ModuleManager.getInstance(project).getModules();

      for (Module module : modules) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          String assembleTaskName = facet.getProperties().ASSEMBLE_TASK_NAME;
          if (isNotEmpty(assembleTaskName)) {
            task = new GoToApkLocationTask("Build APK", module, null);
            break;
          }
        }
      }

      GradleInvoker gradleInvoker = GradleInvoker.getInstance(project);
      if (task != null) {
        gradleInvoker.addAfterGradleInvocationTask(task);
      }
      gradleInvoker.assemble(modules, TestCompileType.NONE);
    }
  }
}
