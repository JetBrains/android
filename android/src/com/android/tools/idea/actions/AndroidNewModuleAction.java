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

import com.android.tools.idea.npw.NewModuleWizardDynamic;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AndroidNewModuleAction extends AnAction implements DumbAware {
  public AndroidNewModuleAction() {
    super("New Module...", "Adds a new module to the project", null);
  }

  public AndroidNewModuleAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      NewModuleWizardDynamic dialog = new NewModuleWizardDynamic(project, null);
      dialog.init();
      dialog.show();
    }
  }
}
