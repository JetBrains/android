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

import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.DeclarationStmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.InvokeExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Local;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.impl.LocalImpl;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;

public class DeclarationStmtImpl implements DeclarationStmt {

  PsiType mPsiType;
  PsiLocalVariable mPsiLocalVariable;
  Local mLocal;
  PsiStatement mPsiStatement;

  @Override
  public PsiType getType() {
    return mPsiType;
  }

  @Override
  public PsiLocalVariable getPsiLocal() {
    return mPsiLocalVariable;
  }

  @Override
  public Local getLocal() {
    return mLocal;
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
  public PsiStatement getPsiRef() {
    return mPsiStatement;
  }

  /**
   * Declaration should not be anything synthesized.
   *
   * @return
   */
  @Override
  public boolean isSynthesize() {
    return false;
  }

  @Override
  public String getSimpleName() {
    return String.format("Decl: %s", mLocal.getSimpleName());
  }


  public DeclarationStmtImpl(PsiType type, PsiLocalVariable psiLocal, PsiStatement psiStmt) {
    this.mPsiType = type;
    this.mPsiLocalVariable = psiLocal;
    this.mPsiStatement = psiStmt;
    LocalImpl curLocal = new LocalImpl(type, psiLocal);
    this.mLocal = curLocal;
  }
}
