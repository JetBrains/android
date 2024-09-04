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

/** PSI element for an augmented assignment statement [expr += expr] */
public class AugmentedAssignmentStatement extends BuildElementImpl implements Statement {

  public AugmentedAssignmentStatement(ASTNode astNode) {
    super(astNode);
  }

  /** Returns the LHS of the assignment */
  @Nullable
  public TargetExpression getLeftHandSideExpression() {
    PsiElement psi = childToPsi(BuildElementTypes.EXPRESSIONS, 0);
    return psi instanceof TargetExpression ? (TargetExpression) psi : null;
  }

  /** Returns the RHS of the assignment */
  @Nullable
  public Expression getAssignedValue() {
    PsiElement psi = childToPsi(BuildElementTypes.EXPRESSIONS, 1);
    return psi instanceof Expression ? (Expression) psi : null;
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitAugmentedAssignmentStatement(this);
  }
}
