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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;

/** PSI element for an binary operation expression [expr BIN_OP expr] */
public class BinaryOpExpression extends BuildElementImpl implements Expression {

  public BinaryOpExpression(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitBinaryOpExpression(this);
  }

  /** Returns the LHS of the expression */
  @Nullable
  public Expression getLhs() {
    PsiElement psi = childToPsi(BuildElementTypes.EXPRESSIONS, 0);
    return psi instanceof Expression ? (Expression) psi : null;
  }

  /** Returns the RHS of the expression */
  @Nullable
  public Expression getRhs() {
    PsiElement psi = childToPsi(BuildElementTypes.EXPRESSIONS, 1);
    return psi instanceof Expression ? (Expression) psi : null;
  }
}
