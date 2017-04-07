/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.experimental.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;

public class PermissionUsageQuickFixRunnable implements Runnable {

  private Project project;
  private Editor editor;

  private static final String TEXT_BEFORE = "if (android.support.v4.content.PermissionChecker.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED) {";
  private static final String TEXT_AFTER = "}";
  PermissionUsageQuickFixRunnable(Project project, Editor editor) {
    this.project = project;
    this.editor = editor;

  }

  @Override
  public void run() {
    final Document document = editor.getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    assert psiFile != null;
    SelectionModel selectionModel = editor.getSelectionModel();
    setLineSelection(document, selectionModel);

    selectionModel = editor.getSelectionModel();

    document.insertString(selectionModel.getSelectionEnd(), TEXT_AFTER);
    document.insertString(selectionModel.getSelectionStart(), TEXT_BEFORE);
    PsiDocumentManager.getInstance(project).commitDocument(document);
    CodeStyleManager.getInstance(project).reformat(psiFile.getNavigationElement());
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiFile.getNavigationElement());
    selectionModel.removeSelection(true);
  }

  public static void setLineSelection(Document document, SelectionModel selectionModel) {
    final int startLineNumber = document.getLineNumber(selectionModel.getSelectionStart());
    final int endLineNumber = document.getLineNumber(selectionModel.getSelectionEnd());
    final int lineStartOffset = document.getLineStartOffset(startLineNumber);
    final int lineEndOffset = document.getLineEndOffset(endLineNumber);
    selectionModel.setSelection(lineStartOffset, lineEndOffset);
  }
}
