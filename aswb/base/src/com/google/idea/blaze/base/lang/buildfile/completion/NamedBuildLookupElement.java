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

import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.intellij.psi.PsiNamedElement;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Generic implementation for {@link com.intellij.psi.PsiNamedElement}s */
public class NamedBuildLookupElement extends BuildLookupElement {

  private final PsiNamedElement element;

  public NamedBuildLookupElement(PsiNamedElement element, QuoteType quoteType) {
    super(element.getName(), quoteType);
    this.element = element;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return element.getIcon(0);
  }
}
