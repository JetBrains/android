/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
import com.intellij.psi.util.PsiTreeUtil;
import javax.annotation.Nullable;

/** PSI element for a parenthesized expression. */
public class ParenthesizedExpression extends BuildElementImpl implements Expression {

  public ParenthesizedExpression(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitParenthesizedExpression(this);
  }

  @Nullable
  public Expression getContainedExpression() {
    Expression expr = this;
    while (expr instanceof ParenthesizedExpression) {
      expr = PsiTreeUtil.getChildOfType(this, Expression.class);
    }
    return expr;
  }
}
