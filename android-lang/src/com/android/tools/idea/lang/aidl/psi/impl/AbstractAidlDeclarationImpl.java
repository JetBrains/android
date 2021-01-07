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

import com.android.tools.idea.lang.aidl.psi.AidlDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlFile;
import com.android.tools.idea.lang.aidl.psi.AidlInterfaceDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlMethodDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlParcelableDeclaration;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import icons.StudioIcons;
import javax.swing.Icon;
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
    return getDeclarationName().getText();
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    String prefix = "";
    if (this instanceof AidlMethodDeclaration) {
      prefix = ((AidlInterfaceDeclaration)this.getParent()).getQualifiedName();
    }
    else {
      prefix = getContainingFile().getPackageName();
    }
    return prefix.isEmpty() ? getName() : prefix + "." + getName();
  }

  @Nullable
  @Override
  public PsiNameIdentifierOwner getGeneratedPsiElement() {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    Module module = ModuleUtilCore.findModuleForPsiElement(this);
    if (module == null) {
      return null;
    }
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);
    if (this instanceof AidlInterfaceDeclaration || this instanceof AidlParcelableDeclaration) {
      return facade.findClass(getQualifiedName(), moduleScope);
    }
    else if (this instanceof AidlMethodDeclaration) {
      AidlDeclaration containedClass = (AidlInterfaceDeclaration)(this.getParent());
      PsiClass psiClass = facade.findClass(containedClass.getQualifiedName(), moduleScope);
      if (psiClass != null) {
        // AIDL doesn't support method overloading, so the generated method can be found using only interface name and method name.
        PsiMethod[] methods = psiClass.findMethodsByName(getDeclarationName().getName(), false);
        return methods.length == 0 ? null : methods[0];
      }
    }
    return null;
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

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @NotNull
      @Override
      public String getPresentableText() {
        return getName();
      }

      @NotNull
      @Override
      public Icon getIcon(boolean unused) {
        return StudioIcons.Common.ANDROID_HEAD;
      }
    };
  }
}
