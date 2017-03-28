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

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Local;
import com.android.tools.idea.experimental.codeanalysis.utils.PsiCFGDebugUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiType;

public class LocalImpl implements Local {

  PsiType mPsiType;
  PsiLocalVariable mPsiLocalRef;

  @Override
  public PsiType getType() {
    return this.mPsiType;
  }

  @Override
  public PsiElement getPsiRef() {
    return this.mPsiLocalRef;
  }

  @Override
  public String getSimpleName() {
    return this.getName();
  }

  public LocalImpl(PsiType type, PsiLocalVariable local) {
    this.mPsiType = type;
    this.mPsiLocalRef = local;
  }

  @Override
  public String getName() {
    if (mPsiLocalRef == null) {
      PsiCFGDebugUtil.LOG.info("Tried to get name from a null local");
      return "";
    }
    return mPsiLocalRef.getName();
  }
}
