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
package com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.impl;

import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.AssignStmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.InvokeExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.tree.IElementType;

public class AssignStmtImpl implements AssignStmt {

  private boolean mbSynthesized;
  private PsiExpression mPsiExpression;
  private Value mLeftOp;
  private Value mRightOp;
  private IElementType mOperator;

  @Override
  public Value getLOp() {
    return mLeftOp;
  }

  @Override
  public Value getROp() {
    return mRightOp;
  }

  @Override
  public void setLOp(Value L) {
    this.mLeftOp = L;
  }

  @Override
  public void setROp(Value R) {
    this.mRightOp = R;
  }

  @Override
  public boolean containsInvokeExpr() {
    if (mRightOp instanceof InvokeExpr) {
      return true;
    }
    return false;
  }

  @Override
  public InvokeExpr getInvokeExpr() {
    if (mRightOp instanceof InvokeExpr) {
      return (InvokeExpr)mRightOp;
    }
    else {
      return null;
    }
  }

  @Override
  public PsiElement getPsiRef() {
    return mPsiExpression;
  }

  @Override
  public boolean isSynthesize() {
    return mbSynthesized;
  }

  @Override
  public String getSimpleName() {
    return String.format(
      "%s %s %s",
      mLeftOp == null ? "[NULL OP]" : mLeftOp.getSimpleName(),
      mOperator.toString(),
      mRightOp == null ? "[NULL OP]" : mRightOp.getSimpleName());
  }

  public AssignStmtImpl(boolean isSynthesized, PsiExpression psiExpr, IElementType opertor) {
    this.mbSynthesized = isSynthesized;
    this.mPsiExpression = psiExpr;
    this.mOperator = opertor;
  }

  @Override
  public IElementType getOperator() {
    return this.mOperator;
  }
}
