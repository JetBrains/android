/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.actions;

import com.android.tools.idea.gradle.util.GradleProjects;
import com.android.tools.idea.refactoring.rtl.RtlSupportManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AndroidAddRtlSupportAction extends AnAction implements DumbAware {

  public AndroidAddRtlSupportAction() {
    super("Add right-to-left (RTL) support...");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      new RtlSupportManager(project).showDialog();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    e.getPresentation().setEnabledAndVisible(module != null && GradleProjects.isIdeaAndroidModule(module));
  }
}
