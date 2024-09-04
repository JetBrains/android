/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.actions;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;

/** The "Blaze" menu group, only visible in blaze mode */
public class BlazeMenuGroup extends DefaultActionGroup {
  @Override
  public final void update(AnActionEvent e) {
    // Don't hide the menu if project is null: it will be null temporarily while loading a
    // Blaze project, and sometimes stays hidden permanently if we hide it during loading.
    Project project = e.getProject();
    if (project != null && !Blaze.isBlazeProject(project)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(true);
    e.getPresentation().setText(menuName(Blaze.getBuildSystemName(e.getProject())));
  }

  private static String menuName(BuildSystemName buildSystemName) {
    switch (buildSystemName) {
      case Bazel:
        return "&Bazel";
      case Blaze:
        return "&Blaze";
    }
    throw new RuntimeException("Unhandled build system: " + buildSystemName);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
