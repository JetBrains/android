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
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Provide fine granularity syntax highlighting based on the PSI element.
 */
public class AidlClassNameAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    final Annotation annotation = holder.createInfoAnnotation(element, null);

    if (element instanceof AidlClassOrInterfaceType) {
      annotation.setTextAttributes(DefaultLanguageHighlighterColors.CLASS_REFERENCE);
    }
    if (element instanceof AidlDeclarationName) {
      PsiElement component = element.getParent();
      if (component instanceof AidlInterfaceDeclaration || component instanceof AidlParcelableDeclaration) {
        annotation.setTextAttributes(DefaultLanguageHighlighterColors.CLASS_NAME);
      }
      else if (element instanceof AidlMethodDeclaration) {
        annotation.setTextAttributes(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
      }
    }
  }
}
