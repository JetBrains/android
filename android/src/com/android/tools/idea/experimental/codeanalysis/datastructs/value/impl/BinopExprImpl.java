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

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.BinopExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;

public class BinopExprImpl implements BinopExpr {
  protected Value mOp1;
  protected Value mOp2;
  protected PsiType mType;
  protected PsiElement mPsiRef;
  protected IElementType mOperator;

  @Override
  public Value getOp1() {
    return mOp1;
  }

  @Override
  public Value getOp2() {
    return mOp2;
  }

  @Override
  public void setOp1(Value op1) {
    this.mOp1 = op1;
  }

  @Override
  public void setOp2(Value op2) {
    this.mOp2 = op2;
  }

  @Override
  public IElementType getOperator() {
    return this.mOperator;
  }

  @Override
  public void setOperator(IElementType operator) {
    this.mOperator = operator;
  }

  @Override
  public PsiType getType() {
    return mType;
  }

  @Override
  public PsiElement getPsiRef() {
    return mPsiRef;
  }

  @Override
  public String getSimpleName() {
    return String.format("%s %s %s", mOp1.getSimpleName(), mOperator.toString(), mOp2.getSimpleName());
  }

  public BinopExprImpl(PsiBinaryExpression binExpr) {
    this.mPsiRef = binExpr;
    this.mType = binExpr.getType();
  }

}
