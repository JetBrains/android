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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LintIdeQuickFix extends WriteActionAware {
  LintIdeQuickFix[] EMPTY_ARRAY = new LintIdeQuickFix[0];

  void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context);

  boolean isApplicable(@NotNull PsiElement startElement,
                       @NotNull PsiElement endElement,
                       @NotNull AndroidQuickfixContexts.ContextType contextType);

  @NotNull
  String getName();

  @Nullable
  default String getFamilyName() { return null; }

  /**
   * Wrapper class allowing a {@link LocalQuickFixOnPsiElement} to be used as a {@link LintIdeQuickFix}
   */
  class LocalFixWrappee implements LintIdeQuickFix {
    private final LocalQuickFixOnPsiElement myFix;

    public LocalFixWrappee(@NotNull LocalQuickFixOnPsiElement fix) {
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

    @Nullable
    @Override
    public String getFamilyName() {
      return myFix.getFamilyName();
    }

    @NotNull
    @Override
    public String getName() {
      return myFix.getName();
    }
  }

  /**
   * Wrapper class allowing an {@link LintIdeQuickFix} to be used as a {@link LocalQuickFix}
   */
  class LocalFixWrapper extends LocalQuickFixOnPsiElement {
    private final LintIdeQuickFix myFix;

    public LocalFixWrapper(@NotNull LintIdeQuickFix fix, @NotNull PsiElement start, @NotNull PsiElement end) {
      super(start, end);
      myFix = fix;
    }

    @NotNull
    @Override
    public String getText() {
      return myFix.getName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myFix.getFamilyName() != null ? myFix.getFamilyName() : myFix.getName();
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      myFix.apply(startElement, endElement, AndroidQuickfixContexts.BatchContext.getInstance());
    }

    @Override
    public boolean startInWriteAction() {
      return myFix.startInWriteAction();
    }
  }
}
