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

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.InstanceOfExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;

public class InstanceOfExprImpl implements InstanceOfExpr {
  private Value mOperand;
  private PsiType mCheckedType;
  private PsiElement mPsiRef;

  @Override
  public Value getOp() {
    return mOperand;
  }

  @Override
  public void setOp(Value v) {
    this.mOperand = v;
  }

  @Override
  public PsiType getCheckType() {
    return mCheckedType;
  }

  @Override
  public void setCheckType(PsiType checkType) {
    this.mCheckedType = checkType;
  }

  @Override
  public PsiType getType() {
    return PsiType.BOOLEAN;
  }

  @Override
  public PsiElement getPsiRef() {
    return mPsiRef;
  }

  @Override
  public String getSimpleName() {
    return String.format("%s instanceof %s", mOperand.getSimpleName(), mCheckedType.toString());
  }

  public InstanceOfExprImpl(PsiType checkedType, PsiElement psiRef) {
    this.mCheckedType = checkedType;
    this.mPsiRef = psiRef;
    this.mOperand = null;
  }
}
