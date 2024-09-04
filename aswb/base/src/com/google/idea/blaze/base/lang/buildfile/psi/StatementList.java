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

import com.google.common.collect.ImmutableList;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;

/** PSI element for a list of statements */
public class StatementList extends BuildListType<Statement> {

  public StatementList(ASTNode astNode) {
    super(astNode, Statement.class);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitStatementList(this);
  }

  @Override
  public int getStartOffset() {
    PsiElement prevSibling = getPrevSibling();
    while (prevSibling != null) {
      if (prevSibling.getText().equals(":")) {
        return prevSibling.getNode().getStartOffset() + 1;
      }
      prevSibling = prevSibling.getPrevSibling();
    }
    return getNode().getStartOffset();
  }

  @Override
  public ImmutableList<Character> getEndChars() {
    return ImmutableList.of();
  }
}
