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

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.search.ResolveUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import javax.annotation.Nullable;

/** Reference from a function call to the function declaration */
public class FuncallReference extends PsiReferenceBase<FuncallExpression> {

  public FuncallReference(FuncallExpression element, TextRange rangeInElement) {
    super(element, rangeInElement, /* soft= */ true);
  }

  @Nullable
  @Override
  public PsiNamedElement resolve() {
    String functionName = myElement.getFunctionName();
    // first search in local scope (e.g. function passed in as an arg).
    PsiNamedElement element = ResolveUtil.findInScope(myElement, functionName);
    if (element != null) {
      return element;
    }
    BuildFile file = myElement.getContainingFile();
    if (functionName == null || file == null) {
      return null;
    }
    return file.findFunctionInScope(functionName);
  }

  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode oldNode = myElement.getFunctionNameNode();
    if (oldNode != null) {
      ASTNode newNode = PsiUtils.createNewName(myElement.getProject(), newElementName);
      myElement.getNode().replaceChild(oldNode, newNode);
    }
    return myElement;
  }
}
