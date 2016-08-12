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
package com.android.tools.idea.experimental.codeanalysis.datastructs.value.impl;

import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.StaticInvokeExpr;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

public class StaticInvokeExprImpl extends InvokeExprImpl implements StaticInvokeExpr {

  protected PsiCFGClass mBase;

  public StaticInvokeExprImpl(PsiCFGMethod cfgMethod,
                              PsiType retType,
                              PsiElement psiRef) {
    super(cfgMethod, retType, psiRef);
  }

  @Override
  public PsiCFGClass getBaseClass() {
    return mBase;
  }

  @Override
  public void setBaseClass(PsiCFGClass base) {
    this.mBase = base;
  }

  @Override
  public String getSimpleName() {
    PsiClass baseClass;
    if (mBase != null) {
      baseClass = mBase.getPsiClass();
    }
    else {
      baseClass = null;
    }

    return String.format("[StaticInvoke]%s.%s",
                         baseClass == null ? "[NULL CLASS]" : baseClass.getQualifiedName(),
                         mTargetMethod == null ? "[NULL METHOD]" : mTargetMethod.getName());
  }
}
