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
package com.android.tools.idea.lang.databinding;

import com.android.tools.idea.lang.databinding.psi.*;
import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DbPairedBraceMatcher extends PairedBraceMatcherAdapter {

  private static final BracePair[] BRACE_PAIRS = new BracePair[]{
    new BracePair(DbTokenTypes.LPARENTH, DbTokenTypes.RPARENTH, false),
    new BracePair(DbTokenTypes.LBRACKET, DbTokenTypes.RBRACKET, false)};

  public DbPairedBraceMatcher() {
    super(new PairedBraceMatcher() {
      @NotNull
      @Override
      public BracePair[] getPairs() {
        return BRACE_PAIRS;
      }

      @Override
      public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
        // TODO: improve this.
        return true;
      }

      @Override
      public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        PsiElement element = file.findElementAt(openingBraceOffset);
        if (element == null || element instanceof PsiFile) {
          return openingBraceOffset;
        }
        PsiElement parent = element.getParent();
        if (parent instanceof PsiDbCastExpr || parent instanceof PsiDbParenExpr) {
          return openingBraceOffset;
        }
        if (parent instanceof PsiDbCallExpr) {
          return parent.getTextRange().getStartOffset();
        }
        if (parent instanceof PsiDbResourceParameters) {
          parent = parent.getParent();
          if (parent instanceof PsiDbResourcesExpr) {
            return parent.getTextRange().getStartOffset();
          }
        }
        return openingBraceOffset;
      }
    }, DbLanguage.INSTANCE);
  }
}
