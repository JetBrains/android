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

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.UnaryExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;

public abstract class UnaryExprImpl implements UnaryExpr {
  protected PsiElement mPsiRef;
  protected IElementType mOperator;
  protected Value mVal;
  protected PsiType mType;

  public UnaryExprImpl(PsiElement psiRef, IElementType operator, Value val, PsiType type) {
    this.mPsiRef = psiRef;
    this.mVal = val;
    this.mOperator = operator;
    this.mType = type;
  }

  @Override
  public IElementType getOperator() {
    return this.mOperator;
  }

  @Override
  public Value getExpr() {
    return this.mVal;
  }

  @Override
  public PsiType getType() {
    return this.mType;
  }

  @Override
  public PsiElement getPsiRef() {
    return this.mPsiRef;
  }
}
