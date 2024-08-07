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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.references.LocalReference;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import javax.annotation.Nullable;

/** References a PsiNamedElement */
public class ReferenceExpression extends BuildElementImpl implements Expression {

  public ReferenceExpression(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  @Nullable
  public ASTNode getNameElement() {
    return getNode().findChildByType(BuildToken.IDENTIFIER);
  }

  @Nullable
  public String getReferencedName() {
    ASTNode node = getNameElement();
    return node != null ? node.getText() : null;
  }

  @Override
  public PsiReference getReference() {
    IElementType parentType = getParentType();
    // function names are resolved by the parent funcall node
    if (BuildElementTypes.FUNCALL_EXPRESSION.equals(parentType) && !beforeDot(getNode())) {
      return null;
    }
    if (BuildElementTypes.GLOB_EXPRESSION.equals(parentType)) {
      return null;
    }
    if (BuildElementTypes.DOT_EXPRESSION.equals(parentType) && !beforeDot(getNode())) {
      return null;
    }
    return new LocalReference(this);
  }

  @Override
  public String getName() {
    return getReferencedName();
  }

  private static boolean beforeDot(ASTNode node) {
    ASTNode prev = node.getTreeNext();
    return prev != null && prev.getText().equals(".");
  }
}
