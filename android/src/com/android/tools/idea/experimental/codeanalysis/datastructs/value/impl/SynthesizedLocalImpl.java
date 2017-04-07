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

import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.AssignStmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.SynthesizedLocal;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;

public class SynthesizedLocalImpl extends LocalImpl implements SynthesizedLocal {

  public PsiElement mPsiElement;
  public String mSynthesizedName;
  public AssignStmt mAssignStmt;

  public SynthesizedLocalImpl(PsiType type, String SynthesizedName, PsiElement element) {
    super(type, null);
    this.mPsiElement = element;
    this.mSynthesizedName = SynthesizedName;
  }

  @Override
  public String getName() {
    return String.format("[SYNLOC %s]", this.mSynthesizedName);
  }

  @Override
  public AssignStmt getAssignmentSite() {
    return mAssignStmt;
  }

  public void setAssignStmt(AssignStmt s) {
    this.mAssignStmt = s;
  }

}
