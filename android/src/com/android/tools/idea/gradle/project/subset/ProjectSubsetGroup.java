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
package com.android.tools.idea.gradle.project.subset;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;

import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;

public class ProjectSubsetGroup extends DefaultActionGroup {
  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    boolean visible = ProjectSubset.isSettingEnabled() &&
                      project != null &&
                      isBuildWithGradle(project) &&
                      ProjectSubset.getInstance(project).hasCachedModules();
    e.getPresentation().setVisible(visible);
  }
}
