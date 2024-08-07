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

import com.google.idea.blaze.base.lang.buildfile.references.TargetReference;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.util.PlatformIcons;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** References a PsiNamedElement */
public class TargetExpression extends NamedBuildElement implements Expression {

  public TargetExpression(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitTargetExpression(this);
  }

  @Override
  public PsiReference getReference() {
    return new TargetReference(this);
  }

  @Nullable
  @Override
  public Icon getIcon(int flags) {
    return PlatformIcons.VARIABLE_ICON;
  }
}
