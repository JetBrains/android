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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.idea.blaze.base.lang.buildfile.completion.CompletionResultsProcessor;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.search.ResolveUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import javax.annotation.Nullable;

/**
 * A reference to an earlier declaration of this symbol (to handle cases where a symbol is the
 * target of multiple assignment statements).
 */
public class TargetReference extends PsiReferenceBase<TargetExpression> {

  public TargetReference(TargetExpression element) {
    super(element, new TextRange(0, element.getTextLength()), /* soft= */ true);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    String referencedName = myElement.getName();
    if (referencedName == null) {
      return null;
    }
    PsiNamedElement target = ResolveUtil.findInScope(myElement, referencedName);
    return target != null ? target : null;
  }

  @Override
  public Object[] getVariants() {
    CompletionResultsProcessor processor =
        new CompletionResultsProcessor(myElement, QuoteType.NoQuotes, true);
    ResolveUtil.searchInScope(myElement, processor);
    return processor.getResults().toArray();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode oldNode = myElement.getNameNode();
    if (oldNode != null) {
      ASTNode newNode = PsiUtils.createNewName(myElement.getProject(), newElementName);
      myElement.getNode().replaceChild(oldNode, newNode);
    }
    return myElement;
  }
}
