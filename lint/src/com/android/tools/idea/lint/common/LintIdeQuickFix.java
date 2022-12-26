/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiFileRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LintIdeQuickFix extends WriteActionAware, PriorityAction {
  LintIdeQuickFix[] EMPTY_ARRAY = new LintIdeQuickFix[0];

  void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context);

  boolean isApplicable(@NotNull PsiElement startElement,
                       @NotNull PsiElement endElement,
                       @NotNull AndroidQuickfixContexts.ContextType contextType);

  @NotNull
  String getName();

  @Nullable
  default String getFamilyName() { return null; }

  void setPriority(@NotNull Priority priority);

  /**
   * Gets a range override to use for this quickfix, if applicable
   */
  @Nullable
  default SmartPsiFileRange getRange() {
    return null;
  }

  /**
   * Returns an override preview for intention actions
   */
  @Nullable
  default IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return null;
  }

  /**
   * Wrapper class allowing a {@link LocalQuickFixOnPsiElement} to be used as a {@link LintIdeQuickFix}
   */
  class LocalFixWrappee extends DefaultLintQuickFix {
    private final LocalQuickFixOnPsiElement myFix;

    public LocalFixWrappee(@NotNull LocalQuickFixOnPsiElement fix) {
      super(fix.getName(), fix.getFamilyName());
      myFix = fix;
    }

    @Override
    public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
      myFix.invoke(startElement.getProject(), startElement.getContainingFile(), startElement, endElement);
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement startElement,
                                @NotNull PsiElement endElement,
                                @NotNull AndroidQuickfixContexts.ContextType contextType) {
      return startElement.isValid();
    }
  }
}
