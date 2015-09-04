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
package com.android.tools.idea.gradle.quickfix;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

abstract class AbstractGradleAwareFix implements IntentionAction, LocalQuickFix, HighPriorityAction {
  @Override
  @NotNull
  public String getName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    invoke(project, null, descriptor.getPsiElement().getContainingFile());
  }

  static void registerUndoAction(@NotNull final Project project) {
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction() {
      @Override
      public void undo() throws UnexpectedUndoException {
        requestProjectSync(project);
      }

      @Override
      public void redo() throws UnexpectedUndoException {
        requestProjectSync(project);
      }
    });
  }

  private static void requestProjectSync(@NotNull Project project) {
    GradleProjectImporter.getInstance().requestProjectSync(project, false, null);
  }

  protected static void runWriteCommandActionAndSync(@NotNull final Project project,
                                                     @NotNull final Runnable action,
                                                     @Nullable final GradleSyncListener syncListener) {
    invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        WriteCommandAction.runWriteCommandAction(project, action);
        GradleProjectImporter.getInstance().requestProjectSync(project, false, syncListener);
      }
    });
  }
}
