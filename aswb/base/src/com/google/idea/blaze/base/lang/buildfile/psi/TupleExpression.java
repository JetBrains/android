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

import com.google.common.collect.ImmutableList;
import com.intellij.lang.ASTNode;

/**
 * PSI element for tuples inside a parenthesized expression. Also used for tuples without enclosing
 * parentheses, not supported in Skylark (accompanied by an appropriate error annotation).
 */
public class TupleExpression extends BuildListType<Expression> implements Expression {

  public TupleExpression(ASTNode astNode) {
    super(astNode, Expression.class);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitTupleExpression(this);
  }

  @Override
  public String getPresentableText() {
    return "tuple";
  }

  public Expression[] getChildExpressions() {
    return findChildrenByClass(Expression.class);
  }

  @Override
  public Expression[] getElements() {
    return getChildExpressions();
  }

  @Override
  public boolean isEmpty() {
    return findChildByClass(Expression.class) != null;
  }

  @Override
  public ImmutableList<Character> getEndChars() {
    return ImmutableList.of(')');
  }
}
