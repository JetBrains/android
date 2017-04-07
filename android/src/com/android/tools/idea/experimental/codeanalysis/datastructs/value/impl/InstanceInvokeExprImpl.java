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

import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.InstanceInvokeExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

public class InstanceInvokeExprImpl extends InvokeExprImpl implements InstanceInvokeExpr {
  protected Value mBase;


  public InstanceInvokeExprImpl(PsiCFGMethod cfgMethod,
                                PsiType retType,
                                PsiElement psiRef) {
    super(cfgMethod, retType, psiRef);
  }

  @Override
  public Value getBase() {
    return this.mBase;
  }

  @Override
  public void setBase(Value v) {
    this.mBase = v;
  }

  @Override
  public String getSimpleName() {
    return String.format("[InstanceInvoke]%s.%s",
                         mBase == null ? "[NULL BASE]" : mBase.getSimpleName(),
                         mTargetMethod == null ? "[NULL METHOD]" : mTargetMethod.getName());
  }
}
