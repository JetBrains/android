/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lang.aidl.psi.impl;

import com.android.tools.idea.lang.aidl.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractAidlDeclarationImpl extends AidlPsiCompositeElementImpl implements AidlDeclaration {
  public AbstractAidlDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public String getName() {
    final AidlDeclarationName name = getDeclarationName();
    return name.getText();
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    AidlFile file = getContainingFile();
    String prefix = file.getPackageName();
    AidlDeclaration containedInterface = getContainedInterface();
    if (containedInterface != null) {
      prefix = containedInterface.getQualifiedName();
    }
    if (prefix.isEmpty()) {
      return getName();
    }
    else {
      return prefix + "." + getName();
    }
  }


  @Override
  public PsiElement setName(@NonNls @NotNull String newName) throws IncorrectOperationException {
    AidlFile file = getContainingFile();
    if ((this instanceof AidlInterfaceDeclaration || this instanceof AidlParcelableDeclaration) && file != null) {
      String oldFileName = file.getName();

      if (oldFileName != null) {
        int dotIndex = oldFileName.lastIndexOf('.');
        String oldName = dotIndex >= 0 ? oldFileName.substring(0, dotIndex) : oldFileName;
        String newFileName = dotIndex >= 0 ? newName + "." + oldFileName.substring(dotIndex + 1) : newName;
        if (getName() != null && getName().equals(oldName)) {
          file.setName(newFileName);
        }
      }
    }
    getDeclarationName().setName(newName);

    return this;
  }

  @Nullable
  @Override
  public PsiElement getNameIdentifier() {
    return getDeclarationName();
  }

  @Nullable
  @Override
  public PsiNameIdentifierOwner getGeneratedPsiElement() {
    // TODO
    return null;
  }

  @Nullable
  public AidlInterfaceDeclaration getContainedInterface() {
    if (this instanceof AidlMethodDeclaration) {
      return (AidlInterfaceDeclaration)getParent();
    } else {
      return null;
    }
  }


  @Override
  public ItemPresentation getPresentation() {
    // TODO
    return null;
  }
}
