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
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.ThisRef;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;

public class ThisRefImpl implements ThisRef {

  private PsiType mType;
  private PsiElement mPsiElement;
  private PsiCFGClass mClass;

  @Override
  public PsiType getType() {
    return mType;
  }

  @Override
  public PsiElement getPsiRef() {
    return mPsiElement;
  }

  @Override
  public String getSimpleName() {
    return "this";
  }

  public ThisRefImpl(PsiThisExpression ref, PsiCFGClass cfgClass, PsiType type) {
    this.mClass = cfgClass;
    this.mType = type;
    this.mPsiElement = ref;

  }

  @Override
  public PsiCFGClass getRefClass() {
    return this.mClass;
  }
}
