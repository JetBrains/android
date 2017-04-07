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

import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.NewExpr;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;

import java.util.ArrayList;

public class NewExprImpl implements NewExpr {
  protected PsiType mType;
  protected PsiCFGClass mBaseClass;
  protected PsiElement mPsiRef;
  protected ArrayList<Value> mArgsList;
  protected boolean mContainsConstructorInvocation;
  protected PsiCFGMethod mConstructorMethod;

  protected boolean mIsArray;


  @Override
  public PsiType getBaseType() {
    return mType;
  }

  @Override
  public PsiCFGClass getBaseClass() {
    return mBaseClass;
  }

  public void setArray() {
    this.mIsArray = true;
  }

  public boolean isArray() {
    return this.mIsArray;
  }

  @Override
  public void setBaseType(PsiType type) {
    this.mType = type;
  }

  @Override
  public void setBaseClass(PsiCFGClass cfgClass) {
    this.mBaseClass = cfgClass;
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
  public boolean containsConstructorInvocation() {
    return this.mContainsConstructorInvocation;
  }

  @Override
  public PsiCFGMethod getConstructorInvocation() {
    return mConstructorMethod;
  }

  public void setConstrctorInvocation(PsiCFGMethod method) {
    this.mContainsConstructorInvocation = true;
    this.mConstructorMethod = method;
  }

  @Override
  public PsiType getType() {
    return mType;
  }

  @Override
  public PsiElement getPsiRef() {
    return this.mPsiRef;
  }

  public ArrayList<Value> getArgsList() {
    return this.mArgsList;
  }

  @Override
  public String getSimpleName() {
    String className;
    if (this.mBaseClass != null) {
      className = this.mBaseClass.getQualifiedClassName();
    }
    else {
      className = "[NULL CLASSREF]";
    }

    return String.format("new %s()",
                         className);
  }

  public NewExprImpl(PsiType baseType, PsiElement psiRef) {
    this.mType = baseType;
    this.mPsiRef = psiRef;
    this.mArgsList = Lists.newArrayList();
    this.mIsArray = false;
    this.mContainsConstructorInvocation = false;
    this.mConstructorMethod = null;
  }
}
