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

import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** PSI element for a function definition statement. */
public class FunctionStatement extends NamedBuildElement
    implements Statement, StatementListContainer, DocStringOwner {

  public FunctionStatement(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitFunctionStatement(this);
  }

  @Nullable
  @Override
  public Icon getIcon(int flags) {
    return PlatformIcons.FUNCTION_ICON;
  }

  @Nullable
  public ParameterList getParameterList() {
    return getPsiChild(BuildElementTypes.PARAMETER_LIST, ParameterList.class);
  }

  public Parameter[] getParameters() {
    ParameterList list = getParameterList();
    return list != null ? list.getElements() : Parameter.EMPTY_ARRAY;
  }

  @Override
  public String getPresentableText() {
    return nonNullName() + getParameterList().getPresentableText();
  }

  @Nullable
  @Override
  public StringLiteral getDocString() {
    StatementList stmtList = getStatementList();
    if (stmtList == null) {
      return null;
    }
    for (PsiElement cur = stmtList.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof StringLiteral
          && ((StringLiteral) cur).getQuoteType() == QuoteType.TripleDouble) {
        return (StringLiteral) cur;
      }
      if (cur instanceof BuildElement) {
        return null;
      }
    }
    return null;
  }
}
