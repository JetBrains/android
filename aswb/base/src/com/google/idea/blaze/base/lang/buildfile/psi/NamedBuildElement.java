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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import javax.annotation.Nullable;

/** Base class for PsiNamedElements in BUILD files. */
public abstract class NamedBuildElement extends BuildElementImpl implements PsiNameIdentifierOwner {

  public NamedBuildElement(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  public ASTNode getNameNode() {
    return getNode().findChildByType(BuildToken.IDENTIFIER);
  }

  @Override
  @Nullable
  public String getName() {
    ASTNode node = getNameNode();
    return node != null ? node.getText() : null;
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    final ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getPsi() : null;
  }

  @CanIgnoreReturnValue
  @Override
  public PsiElement setName(String name) {
    final ASTNode nameElement = PsiUtils.createNewName(getProject(), name);
    final ASTNode nameNode = getNameNode();
    if (nameNode != null) {
      getNode().replaceChild(nameNode, nameElement);
    }
    return this;
  }

  @Override
  public int getTextOffset() {
    final ASTNode name = getNameNode();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  @Override
  public String toString() {
    return super.toString() + "('" + getName() + "')";
  }
}
