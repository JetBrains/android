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

import static com.android.tools.idea.gradle.util.ui.EventUtil.getSelectedAndroidModule;

import com.android.tools.idea.gradle.variant.view.BuildVariantToolWindowFactory;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * Action that allows users to select a build variant for the selected module, if the module is an Android Gradle module.
 */
public class SelectBuildVariantAction extends AndroidStudioGradleAction {
  public SelectBuildVariantAction() {
    super("Select Build Variant...");
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
     e.getPresentation().setEnabled(isGradleProject(e));
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull final Project project) {
    final Module module = getSelectedAndroidModule(e);
    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = manager.getToolWindow(BuildVariantToolWindowFactory.ID);
    if (toolWindow != null) {
      toolWindow.activate(new Runnable() {
        @Override
        public void run() {
          BuildVariantView view = BuildVariantView.getInstance(project);
          if (module != null) {
            view.findAndSelectVariantEditor(module);
          }
        }
      });
    }
  }
}
