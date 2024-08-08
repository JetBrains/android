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
package com.google.idea.blaze.base.lang.projectview.psi;

import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewTokenType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;

/** Psi element for list section. */
public class ProjectViewPsiListSection extends ProjectViewSection {

  public ProjectViewPsiListSection(ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public String getSectionName() {
    PsiElement keyword = findChildByType(ProjectViewTokenType.LIST_KEYWORD);
    return keyword != null ? keyword.getText() : null;
  }
}
