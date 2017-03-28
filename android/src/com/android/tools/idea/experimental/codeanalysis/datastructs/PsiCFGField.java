/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.experimental.codeanalysis.datastructs;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiCFGField implements ClassMember, PsiAnnotationOwner {

  protected PsiCFGClass mDeclearingClass;
  protected PsiField mPsiFieldRef;
  protected int modifierBits;

  public PsiCFGField(@NotNull PsiField field, @NotNull PsiCFGClass clazz) {
    this.mDeclearingClass = clazz;
    this.mPsiFieldRef = field;
    parseModifiers();
  }

  private void parseModifiers() {
    PsiModifierList modifierList = mPsiFieldRef.getModifierList();
    modifierBits = Modifier.ParseModifierList(modifierList);
  }

  public PsiField getPsiFieldRef() {
    return this.mPsiFieldRef;
  }

  @Override
  public PsiCFGClass getDeclaringClass() {
    return this.mDeclearingClass;
  }

  @Override
  public boolean isProtected() {
    return ((modifierBits & Modifier.PROTECTED) != 0);
  }

  @Override
  public boolean isPrivate() {
    return ((modifierBits & Modifier.PRIVATE) != 0);
  }

  @Override
  public boolean isPublic() {
    return ((modifierBits & Modifier.PUBLIC) != 0);
  }

  @Override
  public boolean isStatic() {
    return ((modifierBits & Modifier.STATIC) != 0);
  }

  @NotNull
  @Override
  public PsiAnnotation[] getAnnotations() {
    return new PsiAnnotation[0];
  }

  @NotNull
  @Override
  public PsiAnnotation[] getApplicableAnnotations() {
    return new PsiAnnotation[0];
  }

  @Nullable
  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return null;
  }

  @NotNull
  @Override
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    return null;
  }
}
