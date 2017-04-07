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

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.PolyadicExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;

public class PolyadicExprImpl implements PolyadicExpr {
  ArrayList<Value> mExprList;
  IElementType mOperator;
  PsiType mPsiType;
  PsiPolyadicExpression mPsiRef;

  @Override
  public Value[] getOperands() {
    return mExprList.toArray(Value.EMPTY_ARRAY);
  }

  @Override
  public Value getOperandsAtIndex(int i) {
    return mExprList.get(i);
  }

  @Override
  public int getCounts() {
    return mExprList.size();
  }

  @Override
  public IElementType getOperator() {
    return this.mOperator;
  }

  @Override
  public PsiType getType() {
    return this.mPsiType;
  }

  @Override
  public PsiElement getPsiRef() {
    return this.mPsiRef;
  }

  @Override
  public String getSimpleName() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < mExprList.size(); i++) {
      Value v = mExprList.get(i);
      sb.append(v.getSimpleName());
      if (i != mExprList.size() - 1) {
        sb.append(" ");
        sb.append(mOperator.toString());
        sb.append(" ");
      }
    }
    return sb.toString();
  }

  public void addOperand(Value v) {
    this.mExprList.add(v);
  }

  public PolyadicExprImpl(IElementType operator, PsiPolyadicExpression psiRef) {
    this.mOperator = operator;
    this.mPsiRef = psiRef;
    this.mExprList = Lists.newArrayList();
    this.mPsiType = this.mPsiRef.getType();
  }
}
