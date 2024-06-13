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

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiFileRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Legacy base class for Lint quick fixes running on the EDT.
 */
public class DefaultLintQuickFix implements LintIdeQuickFix {
  protected final String myName;
  protected final String myFamilyName;

  public DefaultLintQuickFix(@Nullable String name) {
    this(name, null);
  }

  public DefaultLintQuickFix(@Nullable String name, @Nullable String familyName) {
    // Name must not be null unless getName is overridden!
    myName = name;
    myFamilyName = familyName;
  }

  public DefaultLintQuickFix(@Nullable String name, boolean useAsFamilyNameToo) { // to use as family name, the description must be general
    this(name, useAsFamilyNameToo ? name : null);
  }

  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
  }

  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return true;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getFamilyName() {
    return myFamilyName;
  }

  private Priority priority = Priority.HIGH;

  @Override
  public @NotNull Priority getPriority() {
    return priority;
  }

  @Override
  public void setPriority(@NotNull Priority priority) {
    this.priority = priority;
  }

  /**
   * Gets a range override to use for this quickfix, if applicable
   */
  @Nullable
  public SmartPsiFileRange getRange() {
    return null;
  }

  /**
   * Returns an override preview for intention actions
   */
  @Nullable
  public IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return null;
  }
}
