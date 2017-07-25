/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.command;

import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.openapi.application.BaseActionRunnable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class NlWriteCommandAction implements Runnable {
  private final Project myProject;
  private final String myName;
  private final PsiFile myFile;
  private final Runnable myRunnable;

  public NlWriteCommandAction(@NotNull Project project, @NotNull String name, @NotNull PsiFile file, @NotNull Runnable runnable) {
    myProject = project;
    myName = name;
    myFile = file;
    myRunnable = runnable;
  }

  @Override
  public void run() {
    BaseActionRunnable<Void> action = new WriteCommandAction.Simple<Void>(myProject, myName, myFile) {
      @Override
      protected void run() throws Throwable {
        myRunnable.run();
        TemplateUtils.reformatAndRearrange(myProject, myFile);
      }
    };

    action.execute();
  }
}