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

import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Base PSI class for the BUILD language */
public abstract class BuildElementImpl extends ASTWrapperPsiElement implements BuildElement {

  public BuildElementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  public <P extends PsiElement> P getPsiChild(IElementType type, Class<P> psiClass) {
    ASTNode childNode = getNode().findChildByType(type);
    return childNode != null && psiClass.isInstance(childNode.getPsi())
        ? psiClass.cast(childNode.getPsi())
        : null;
  }

  @Override
  public <P extends PsiElement> P[] childrenOfClass(Class<P> psiClass) {
    return findChildrenByClass(psiClass);
  }

  @Nullable
  @Override
  public <P extends PsiElement> P firstChildOfClass(Class<P> psiClass) {
    return findChildByClass(psiClass);
  }

  /** Finds the n'th child of the specified type */
  @Nullable
  protected PsiElement childToPsi(TokenSet filterSet, int index) {
    final ASTNode[] nodes = getNode().getChildren(filterSet);
    if (nodes.length <= index) {
      return null;
    }
    return nodes[index].getPsi();
  }

  @Nullable
  protected IElementType getParentType() {
    ASTNode node = getNode().getTreeParent();
    return node != null ? node.getElementType() : null;
  }

  public String nonNullName() {
    String name = getName();
    return name != null ? name : "<unnamed>";
  }

  @Override
  public String getPresentableText() {
    return nonNullName();
  }

  @Override
  public String toString() {
    return super.toString() + ": " + getPresentableText();
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof BuildElementVisitor) {
      acceptVisitor(((BuildElementVisitor) visitor));
    } else {
      super.accept(visitor);
    }
  }

  protected abstract void acceptVisitor(BuildElementVisitor visitor);

  @Nullable
  @Override
  public PsiElement getReferencedElement() {
    PsiReference[] refs = getReferences();
    for (PsiReference ref : refs) {
      PsiElement element = ref.resolve();
      if (element != null) {
        return element;
      }
    }
    return null;
  }

  @Override
  public ItemPresentation getPresentation() {
    final BuildElement element = this;
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return element.getPresentableText();
      }

      @Override
      public String getLocationString() {
        return element.getLocationString();
      }

      @Override
      public Icon getIcon(boolean unused) {
        return element.getIcon(0);
      }
    };
  }

  @Nullable
  @Override
  public BlazePackage getBlazePackage() {
    PsiFile file = getContainingFile();
    return file != null ? BlazePackage.getContainingPackage(file) : null;
  }

  @Nullable
  @Override
  public BuildFile getContainingFile() {
    return (BuildFile) super.getContainingFile();
  }
}
