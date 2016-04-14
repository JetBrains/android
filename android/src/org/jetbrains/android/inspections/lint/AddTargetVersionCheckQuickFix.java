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
package org.jetbrains.android.inspections.lint;

import com.android.sdklib.SdkVersionInfo;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/** Fix which surrounds an API warning with a version check */
class AddTargetVersionCheckQuickFix implements AndroidLintQuickFix {
  private int myApi;

  AddTargetVersionCheckQuickFix(int api) {
    myApi = api;
  }

  @NotNull
  static String getVersionField(int myApi, boolean fullyQualified) {
    String codeName = SdkVersionInfo.getBuildCode(myApi);
    if (codeName == null) {
      return Integer.toString(myApi);
    } else if (fullyQualified) {
      return "android.os.Build.VERSION_CODES." + codeName;
    } else {
      return codeName;
    }
  }

  @NotNull
  @Override
  public String getName() {
    return "Surround with if (VERSION.SDK_INT >= VERSION_CODES." + getVersionField(myApi, false) + ") { ... }";
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    PsiExpression expression = PsiTreeUtil.getParentOfType(startElement, PsiExpression.class, false);
    return expression != null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    PsiExpression expression = PsiTreeUtil.getParentOfType(startElement, PsiExpression.class, false);
    if (expression == null) {
      return;
    }

    PsiStatement anchorStatement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
    Editor editor = PsiUtilBase.findEditor(expression);
    if (editor == null) {
      return;
    }
    PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(startElement, PsiModifierListOwner.class, false);
    PsiFile file = expression.getContainingFile();
    Project project = expression.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(file);
    if (document == null) {
      return;
    }
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
      return;
    }
    PsiElement[] elements = {anchorStatement};
    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(anchorStatement, PsiWhiteSpace.class);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      elements = new PsiElement[]{prev, anchorStatement};
    }
    try {
      TextRange textRange = new JavaWithIfSurrounder().surroundElements(project, editor, elements);
      if (textRange == null) {
        return;
      }

      @NonNls String newText = "android.os.Build.VERSION.SDK_INT >= " + getVersionField(myApi, true);
      document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), newText);
      documentManager.commitDocument(document);

      editor.getCaretModel().moveToOffset(textRange.getEndOffset() + newText.length());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

      if (owner != null && owner.isValid() && !ApplicationManager.getApplication().isUnitTestMode()) { // Unit tests: "JavaDummyHolder" doesn't work
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(owner);
      }
    }
    catch (IncorrectOperationException e) {
      Logger.getInstance(AddTargetVersionCheckQuickFix.class).error(e);
    }
  }
}
