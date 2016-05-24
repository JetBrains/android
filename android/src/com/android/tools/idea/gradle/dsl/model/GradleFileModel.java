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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.parser.GradleDslFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

public abstract class GradleFileModel {
  @NotNull protected GradleDslFile myGradleDslFile;

  public GradleFileModel(@NotNull GradleDslFile gradleDslFile) {
    myGradleDslFile = gradleDslFile;
  }

  @NotNull
  public Project getProject() {
    return myGradleDslFile.getProject();
  }

  public void reparse() {
    myGradleDslFile.reparse();
  }

  public boolean isModified() {
    return myGradleDslFile.isModified();
  }

  public void resetState() {
    myGradleDslFile.resetState();
  }

  public void applyChanges() {
    myGradleDslFile.applyChanges();

    // Check for any postponed psi operations and complete them to unblock the underlying document for further modifications.
    GroovyPsiElement psiElement = myGradleDslFile.getPsiElement();
    assert psiElement instanceof PsiFile;

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
    Document document = psiDocumentManager.getDocument((PsiFile)psiElement);
    if (document == null) {
      return;
    }

    if (psiDocumentManager.isDocumentBlockedByPsi(document)) {
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
    }
  }
}
