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
package com.android.tools.idea.lang.aidl.highlight;

import com.android.tools.idea.lang.aidl.psi.*;
import com.intellij.lang.annotation.*;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Provide fine granularity syntax highlighting based on the PSI element.
 */
public class AidlClassNameAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    AnnotationBuilder builder = holder.newSilentAnnotation(HighlightSeverity.INFORMATION);

    if (element instanceof AidlClassOrInterfaceType) {
      builder = builder.textAttributes(DefaultLanguageHighlighterColors.CLASS_REFERENCE);
    }
    else if (element instanceof AidlDeclarationName) {
      PsiElement component = element.getParent();
      if (component instanceof AidlInterfaceDeclaration || component instanceof AidlParcelableDeclaration) {
        builder = builder.textAttributes(DefaultLanguageHighlighterColors.CLASS_NAME);
      }
      else if (element instanceof AidlMethodDeclaration) {
        builder = builder.textAttributes(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
      }
    }
    builder.create();
  }
}
