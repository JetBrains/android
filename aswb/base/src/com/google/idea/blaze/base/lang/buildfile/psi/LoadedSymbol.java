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

import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;

/** PSI element for a loaded symbol within a load statement (either a StringLiteral or an alias). */
public class LoadedSymbol extends BuildElementImpl implements Expression {

  public LoadedSymbol(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitLoadedSymbol(this);
  }

  @Nullable
  public String getSymbolString() {
    PsiElement firstChild = getFirstChild();
    if (firstChild instanceof StringLiteral) {
      return ((StringLiteral) firstChild).getStringContents();
    }
    if (firstChild instanceof AssignmentStatement) {
      return ((AssignmentStatement) firstChild).getName();
    }
    return null;
  }

  @Nullable
  public StringLiteral getImport() {
    return PsiUtils.findFirstChildOfClassRecursive(this, StringLiteral.class);
  }

  /** The PsiElement referenced in the loaded extension. */
  @Nullable
  public PsiElement getLoadedElement() {
    StringLiteral literal = getImport();
    return literal != null ? literal.getReferencedElement() : null;
  }

  /**
   * If the symbol is aliased, stops there, otherwise continues to source. This is to support usage
   * highlighting within a file.
   */
  @Nullable
  public PsiElement getVisibleElement() {
    PsiElement firstChild = getFirstChild();
    if (firstChild instanceof StringLiteral) {
      return ((StringLiteral) firstChild).getReferencedElement();
    }
    if (firstChild instanceof AssignmentStatement) {
      return ((AssignmentStatement) firstChild).getLeftHandSideExpression();
    }
    return null;
  }
}
