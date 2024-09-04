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

import com.google.idea.blaze.base.lang.buildfile.references.FileLookupData.PathFormat;
import com.google.idea.blaze.base.lang.projectview.language.ProjectViewKeywords;
import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewTokenType;
import com.google.idea.blaze.base.lang.projectview.references.ProjectViewLabelReference;
import com.google.idea.blaze.base.projectview.section.SectionParser.ItemType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import javax.annotation.Nullable;

/** Psi element for a list or scalar item. */
public abstract class ProjectViewPsiSectionItem extends ProjectViewPsiElement {

  public ProjectViewPsiSectionItem(ASTNode node) {
    super(node);
  }

  @Override
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  @Override
  public PsiReference getReference() {
    ASTNode identifier = getNode().findChildByType(ProjectViewTokenType.IDENTIFIER);
    PathFormat pathFormat = getLabelType();
    if (identifier != null && pathFormat != null) {
      return new ProjectViewLabelReference(this, pathFormat);
    }
    return null;
  }

  @Nullable
  public PathFormat getLabelType() {
    ASTNode parent = getNode().getTreeParent();
    ASTNode identifier = parent != null ? parent.getFirstChildNode() : null;
    if (identifier == null) {
      return null;
    }
    ItemType itemType = ProjectViewKeywords.ITEM_TYPES.get(identifier.getText());
    if (itemType == null) {
      return null;
    }
    switch (itemType) {
      case Label:
        return PathFormat.NonLocal;
      case FileItem:
        return PathFormat.NonLocalWithoutInitialBackslashes;
      case DirectoryItem:
        return PathFormat.NonLocalWithoutInitialBackslashesOnlyDirectories;
      case Other:
        return null;
    }
    throw new RuntimeException("Unhandled ItemType enum value: " + itemType);
  }
}
