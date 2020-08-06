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

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;

/**
 * A Temporary place holder for the hard
 * to solve issue of ReferenceExpression in the PsiTree
 */
public class TemporaryRef implements Ref {
  private PsiType mType;
  private PsiElement mPsiRef;

  @Override
  public PsiType getType() {
    return mType;
  }

  @Override
  public PsiElement getPsiRef() {
    return mPsiRef;
  }

  @Override
  public String getSimpleName() {
    if (mPsiRef != null) {
      return "TemporaryRef " + mPsiRef.getText();
    }
    else {
      return "TemporaryRef";
    }
  }

  public TemporaryRef(PsiType type, PsiElement psiRef) {
    this.mPsiRef = psiRef;
    this.mType = type;
  }
}
