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

import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import javax.annotation.Nullable;

/**
 * Reference from keyword argument to a named function parameter. TODO: This is soft, because we
 * can't always find the function. However we should implement error highlighting for imported
 * Skylark functions.
 */
public class KeywordArgumentReference extends ArgumentReference<Argument.Keyword> {

  public KeywordArgumentReference(Argument.Keyword element, TextRange rangeInElement) {
    super(element, rangeInElement, false);
  }

  /**
   * Find the referenced function. If it has a keyword parameter with matching name, return that.
   * Otherwise if it has a **kwargs param, return that. Else return the function itself.
   */
  @Nullable
  @Override
  public PsiElement resolve() {
    String keyword = myElement.getName();
    if (keyword == null) {
      return null;
    }
    FunctionStatement function = resolveFunction();
    if (function == null) {
      return null;
    }
    Parameter.StarStar kwargsParameter = null;
    for (Parameter param : function.getParameters()) {
      if (param instanceof Parameter.StarStar) {
        kwargsParameter = (Parameter.StarStar) param;
        continue;
      }
      if (keyword.equals(param.getName())) {
        return param;
      }
    }
    if (kwargsParameter != null) {
      return kwargsParameter;
    }
    return null;
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
