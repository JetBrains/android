/*
 * Copyright (C) 2019 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from proguardR8.bnf. Do not edit it manually.

package com.android.tools.idea.lang.proguardR8.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.android.tools.idea.lang.proguardR8.psi.*;
import com.intellij.psi.PsiClass;

public class ProguardR8ClassSpecificationHeaderImpl extends ASTWrapperPsiElement implements ProguardR8ClassSpecificationHeader {

  public ProguardR8ClassSpecificationHeaderImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ProguardR8Visitor visitor) {
    visitor.visitClassSpecificationHeader(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ProguardR8Visitor) accept((ProguardR8Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ProguardR8AnnotationName> getAnnotationNameList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ProguardR8AnnotationName.class);
  }

  @Override
  @NotNull
  public List<ProguardR8ClassModifier> getClassModifierList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ProguardR8ClassModifier.class);
  }

  @Override
  @NotNull
  public List<ProguardR8ClassName> getClassNameList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ProguardR8ClassName.class);
  }

  @Override
  @NotNull
  public ProguardR8ClassType getClassType() {
    return findNotNullChildByClass(ProguardR8ClassType.class);
  }

  @Override
  @NotNull
  public List<PsiClass> resolvePsiClasses() {
    return ProguardR8PsiImplUtil.resolvePsiClasses(this);
  }

}
