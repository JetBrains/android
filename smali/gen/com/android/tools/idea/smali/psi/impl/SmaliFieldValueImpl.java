/*
 * Copyright (C) 2017 The Android Open Source Project
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

// Generated from Smali.bnf, do not modify
package com.android.tools.idea.smali.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.smali.psi.SmaliTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.android.tools.idea.smali.psi.*;

public class SmaliFieldValueImpl extends ASTWrapperPsiElement implements SmaliFieldValue {

  public SmaliFieldValueImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull SmaliVisitor visitor) {
    visitor.visitFieldValue(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof SmaliVisitor) accept((SmaliVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public SmaliSingleValue getSingleValue() {
    return findChildByClass(SmaliSingleValue.class);
  }

}
