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
package org.jetbrains.android.intentions;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.inspections.lint.ParcelableQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.android.inspections.lint.ParcelableQuickFix.Operation.IMPLEMENT;

public class ImplementParcelableAction extends AbstractIntentionAction implements HighPriorityAction {
  private final ParcelableQuickFix myQuickFix;

  public ImplementParcelableAction() {
    this(IMPLEMENT);
  }

  protected ImplementParcelableAction(ParcelableQuickFix.Operation operation) {
    myQuickFix = new ParcelableQuickFix(getText(), operation);
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return AndroidBundle.message("implement.parcelable.intention.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement element = getPsiElement(file, editor);
    return element != null && myQuickFix.isApplicable(element, element, AndroidQuickfixContexts.DesignerContext.TYPE);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = getPsiElement(file, editor);
    myQuickFix.apply(element, element, AndroidQuickfixContexts.DesignerContext.getInstance());
  }

  @Nullable
  private static PsiElement getPsiElement(PsiFile file, Editor editor) {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    return element != null ? element.getParent() : null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
