/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.idea.blaze.base.lang.buildfile.psi.IntegerLiteral;
import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;

/** Allows conditionally skipping auto-popup of completion results. */
class BuildFileCompletionConfidence extends CompletionConfidence {

  @Override
  public ThreeState shouldSkipAutopopup(PsiElement contextElement, PsiFile psiFile, int offset) {
    if (contextElement.getParent() instanceof IntegerLiteral) {
      return ThreeState.YES;
    }
    return super.shouldSkipAutopopup(contextElement, psiFile, offset);
  }
}
