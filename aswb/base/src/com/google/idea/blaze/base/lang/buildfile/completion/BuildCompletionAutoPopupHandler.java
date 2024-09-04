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
package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Supplements {@link com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler}, triggering
 * auto-complete pop-up on some non-letter characters when typing in build files.
 */
public class BuildCompletionAutoPopupHandler extends TypedHandlerDelegate {

  @Override
  public Result checkAutoPopup(
      char charTyped, final Project project, final Editor editor, final PsiFile file) {
    if (!(file instanceof BuildFile)) {
      return Result.CONTINUE;
    }
    if (LookupManager.getActiveLookup(editor) != null) {
      return Result.CONTINUE;
    }

    if (charTyped != '/' && charTyped != ':') {
      return Result.CONTINUE;
    }
    PsiElement psi = file.findElementAt(editor.getCaretModel().getOffset());
    if (psi != null && psi.getParent() instanceof StringLiteral) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }
}
