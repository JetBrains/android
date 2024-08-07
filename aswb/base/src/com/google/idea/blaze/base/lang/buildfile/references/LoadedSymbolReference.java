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
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import javax.annotation.Nullable;

/** References from load statement string to a function or variable in a Skylark extension */
public class LoadedSymbolReference extends PsiReferenceBase<StringLiteral> {

  private final LabelReference bzlFileReference;

  public LoadedSymbolReference(StringLiteral element, LabelReference bzlFileReference) {
    super(element, new TextRange(0, element.getTextLength()), /* soft= */ false);
    this.bzlFileReference = bzlFileReference;
  }

  @Nullable
  @Override
  public BuildElement resolve() {
    PsiElement bzlFile = bzlFileReference.resolve();
    if (!(bzlFile instanceof BuildFile)) {
      return null;
    }
    return ((BuildFile) bzlFile).findSymbolInScope(myElement.getStringContents());
  }

  @Override
  public Object[] getVariants() {
    PsiElement bzlFile = bzlFileReference.resolve();
    if (!(bzlFile instanceof BuildFile)) {
      return EMPTY_ARRAY;
    }
    CompletionResultsProcessor processor =
        new CompletionResultsProcessor(myElement, myElement.getQuoteType(), false);
    ((BuildFile) bzlFile).searchSymbolsInScope(processor, null);
    return processor.getResults().toArray();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode newNode = PsiUtils.createNewLabel(myElement.getProject(), newElementName);
    myElement.getNode().replaceChild(myElement.getNode().getFirstChildNode(), newNode);
    return myElement;
  }
}
