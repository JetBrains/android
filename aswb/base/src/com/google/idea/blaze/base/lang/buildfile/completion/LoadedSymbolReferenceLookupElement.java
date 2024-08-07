/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.lang.buildfile.psi.LoadedSymbol;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * We calculate the referenced element lazily, as it often won't be needed (e.g. when the string
 * doesn't match the string fragment being completed.
 */
public class LoadedSymbolReferenceLookupElement extends BuildLookupElement {

  private final LoadedSymbol loadedSymbol;
  private final NullableLazyValue<PsiElement> referencedElement =
      new NullableLazyValue<PsiElement>() {
        @Nullable
        @Override
        protected PsiElement compute() {
          return loadedSymbol.getVisibleElement();
        }
      };

  public LoadedSymbolReferenceLookupElement(
      LoadedSymbol loadedSymbol, String symbolString, QuoteType quoteType) {
    super(symbolString, quoteType);
    this.loadedSymbol = loadedSymbol;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    PsiElement ref = referencedElement.getValue();
    return ref != null ? ref.getIcon(0) : null;
  }
}
