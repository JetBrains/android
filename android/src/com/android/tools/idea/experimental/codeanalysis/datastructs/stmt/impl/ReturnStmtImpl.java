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

import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.ReturnStmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.InvokeExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class ReturnStmtImpl implements ReturnStmt {

  protected Value mReturnValue;
  protected PsiElement mPsiRef;

  @Nullable
  @Override
  public Value getReturnValue() {
    return this.mReturnValue;
  }

  @Override
  public boolean containsInvokeExpr() {
    return false;
  }

  @Override
  public InvokeExpr getInvokeExpr() {
    return null;
  }

  @Override
  public PsiElement getPsiRef() {
    return this.mPsiRef;
  }

  @Override
  public boolean isSynthesize() {
    return false;
  }

  @Override
  public String getSimpleName() {
    return String.format("return %s", mReturnValue == null ? "" : mReturnValue.getSimpleName());
  }

  public ReturnStmtImpl(Value returnVal, PsiElement psiRef) {
    this.mReturnValue = returnVal;
    this.mPsiRef = psiRef;
  }
}
