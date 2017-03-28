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
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.InvokeExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import java.util.ArrayList;

public abstract class InvokeExprImpl implements InvokeExpr {
  protected PsiCFGMethod mTargetMethod;
  protected ArrayList<Value> mArgsList;
  protected PsiType mPsiReturnType;
  protected PsiElement mPsiRef;


  @Override
  public PsiCFGMethod getMethod() {
    return mTargetMethod;
  }

  @Override
  public Value[] getArgs() {
    return mArgsList.toArray(Value.EMPTY_ARRAY);
  }

  @Override
  public int getArgCount() {
    return mArgsList.size();
  }

  @Override
  public Value getArg(int index) {
    return mArgsList.get(index);
  }

  @Override
  public PsiType getType() {
    return mPsiReturnType;
  }

  @Override
  public PsiElement getPsiRef() {
    return this.mPsiRef;
  }

  @Override
  public String getSimpleName() {
    return "";
  }

  public void addArg(Value arg) {
    this.mArgsList.add(arg);
  }

  public InvokeExprImpl(PsiCFGMethod cfgMethod, PsiType retType, PsiElement psiRef) {
    this.mTargetMethod = cfgMethod;
    this.mPsiRef = psiRef;
    this.mPsiReturnType = retType;
    this.mArgsList = Lists.newArrayList();
  }
}
