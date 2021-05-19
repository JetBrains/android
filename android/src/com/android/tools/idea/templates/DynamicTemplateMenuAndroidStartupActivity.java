/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.AndroidStartupActivity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

class DynamicTemplateMenuAndroidStartupActivity implements AndroidStartupActivity {
  private static boolean ourDynamicTemplateMenuCreated;

  @Override
  @UiThread
  public void runActivity(@NotNull Project project, @NotNull Disposable disposable) {
    createDynamicTemplateMenu();
  }

  private static void createDynamicTemplateMenu() {
    if (ourDynamicTemplateMenuCreated) {
      return;
    }
    ourDynamicTemplateMenuCreated = true;

    new Task.Backgroundable(null, "Loading Dynamic Templates", false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      ActionGroup menu;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        menu = TemplateManager.getInstance().getTemplateCreationMenu();
      }

      @Override
      public void onFinished() {
        DefaultActionGroup newGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("NewGroup");
        newGroup.addSeparator();
        if (menu != null) {
          newGroup.add(menu, new Constraints(Anchor.AFTER, "NewFromTemplate"));
        }
      }
    }.queue();
  }
}
