/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.validation;

import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;

/** A {@link SpellcheckingStrategy} for BUILD/bzl files */
public class BuildSpellcheckingStrategy extends SpellcheckingStrategy {
  @Override
  public boolean isMyContext(PsiElement element) {
    if (element instanceof PsiComment) {
      return true;
    }
    if (element instanceof StringLiteral) {
      // as a rough heuristic, only spell-check triple-quoted strings, which are more likely to be
      // english sentences rather than keywords
      QuoteType q = ((StringLiteral) element).getQuoteType();
      return q == QuoteType.TripleDouble || q == QuoteType.TripleSingle;
    }
    return false;
  }

  @Override
  public Tokenizer<?> getTokenizer(PsiElement element) {
    if (element instanceof StringLiteral) {
      return TEXT_TOKENIZER;
    }
    return super.getTokenizer(element);
  }
}
