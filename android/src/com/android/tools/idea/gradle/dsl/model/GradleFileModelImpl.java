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

import com.android.tools.idea.gradle.dsl.api.GradleFileModel;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GradleFileModelImpl implements GradleFileModel {
  @NotNull protected GradleDslFile myGradleDslFile;

  public GradleFileModelImpl(@NotNull GradleDslFile gradleDslFile) {
    myGradleDslFile = gradleDslFile;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return myGradleDslFile.getPsiElement();
  }

  @NotNull
  @Override
  public Project getProject() {
    return myGradleDslFile.getProject();
  }

  @Override
  public void reparse() {
    myGradleDslFile.reparse();
  }

  @Override
  public boolean isModified() {
    return myGradleDslFile.isModified();
  }

  @Override
  public void resetState() {
    myGradleDslFile.resetState();
  }

  @NotNull
  @Override
  public VirtualFile getVirtualFile() {
    return myGradleDslFile.getFile();
  }

  @Override
  public void applyChanges() {
    myGradleDslFile.applyChanges();

    // Check for any postponed psi operations and complete them to unblock the underlying document for further modifications.
    PsiElement psiElement = myGradleDslFile.getPsiElement();
    assert psiElement instanceof PsiFile;

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
    Document document = psiDocumentManager.getDocument((PsiFile)psiElement);
    if (document == null) {
      return;
    }

    if (psiDocumentManager.isDocumentBlockedByPsi(document)) {
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
    }

    // Save the file to disk to ensure the changes exist when it is read.
    FileDocumentManager.getInstance().saveDocument(document);
  }
}
