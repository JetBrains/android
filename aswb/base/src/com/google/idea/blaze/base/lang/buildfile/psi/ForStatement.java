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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import java.util.List;

/** PSI element for a for statement. */
public class ForStatement extends BuildElementImpl implements Statement, StatementListContainer {

  public ForStatement(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitForStatement(this);
  }

  public List<Expression> getForLoopVariables() {
    List<Expression> loopVariableExpressions = Lists.newArrayList();
    for (PsiElement child : getChildren()) {
      if (child.getNode().getElementType() == BuildToken.fromKind(TokenKind.IN)) {
        return loopVariableExpressions;
      }
      if (child instanceof Expression) {
        loopVariableExpressions.add((Expression) child);
      } else if (child instanceof ListLiteral) {
        for (Expression expr : ((ListLiteral) child).childrenOfClass(Expression.class)) {
          loopVariableExpressions.add(expr);
        }
      }
    }
    return loopVariableExpressions;
  }

  @Override
  public String getPresentableText() {
    return "for loop";
  }
}
