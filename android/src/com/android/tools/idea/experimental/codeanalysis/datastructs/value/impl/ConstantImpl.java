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

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Constant;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;


public class ConstantImpl implements Constant {

  private PsiLiteralExpression mLiteral;

  @Override
  public PsiType getType() {
    return mLiteral.getType();
  }

  @Override
  public PsiElement getPsiRef() {
    return mLiteral;
  }

  @Override
  public String getSimpleName() {
    return mLiteral.getText();
  }

  @Override
  public PsiLiteralExpression getPsiLiteralExpr() {
    return mLiteral;
  }

  public ConstantImpl(PsiLiteralExpression literalExpr) {
    this.mLiteral = literalExpr;
  }
}
