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
package com.android.tools.idea.lang.databinding.model;

import android.databinding.tool.reflection.ModelClass;
import android.databinding.tool.reflection.ModelMethod;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;

import java.util.List;

public class PsiModelMethod extends ModelMethod {
  PsiMethod myPsiMethod;

  public PsiModelMethod(PsiMethod psiMethod) {
    myPsiMethod = psiMethod;
  }

  @Override
  public ModelClass getDeclaringClass() {
    PsiClass containingClass = myPsiMethod.getContainingClass();
    return containingClass == null ? null : new PsiModelClass(PsiTypesUtil.getClassType(containingClass));
  }

  public PsiMethod getPsiMethod() {
    return myPsiMethod;
  }

  @Override
  public ModelClass[] getParameterTypes() {
    PsiParameterList parameterList = myPsiMethod.getParameterList();
    ModelClass[] modelClasses = new ModelClass[parameterList.getParameters().length];
    for (int i = 0; i < parameterList.getParametersCount(); i++) {
      PsiParameter param = parameterList.getParameters()[i];
      modelClasses[i] = new PsiModelClass(param.getType());
    }
    return modelClasses;
  }

  @Override
  public String getName() {
    return myPsiMethod.getName();
  }

  @Override
  public ModelClass getReturnType(List<ModelClass> list) {
    PsiType returnType = myPsiMethod.getReturnType();
    return returnType != null ? new PsiModelClass(returnType) : null;
  }

  @Override
  public boolean isVoid() {
    return PsiType.VOID.equals(myPsiMethod.getReturnType());
  }

  @Override
  public boolean isPublic() {
    return myPsiMethod.hasModifierProperty(PsiModifier.PUBLIC);
  }

  @Override
  public boolean isStatic() {
    return myPsiMethod.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public boolean isAbstract() {
    return myPsiMethod.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  @Override
  public boolean isBindable() {
    return false;
  }

  @Override
  public int getMinApi() {
    return 0;
  }

  @Override
  public String getJniDescription() {
    return myPsiMethod.getName();
  }

  @Override
  public boolean isVarArgs() {
    return myPsiMethod.isVarArgs();
  }
}
