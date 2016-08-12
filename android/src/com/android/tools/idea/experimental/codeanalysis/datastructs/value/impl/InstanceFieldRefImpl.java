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

import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGField;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.InstanceFieldRef;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;

public class InstanceFieldRefImpl extends FieldRefImpl implements InstanceFieldRef {
  Value mBase;

  public InstanceFieldRefImpl(PsiType type, PsiCFGField cfgFieldRef, PsiElement psiElementRef) {
    this.mType = type;
    this.mFieldRef = cfgFieldRef;
    this.mPsiElement = psiElementRef;
  }

  @Override
  public Value getBase() {
    return mBase;
  }

  @Override
  public void setBase(Value base) {
    this.mBase = base;
  }
}
