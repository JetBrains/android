/*
 * Copyright (C) 2024 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from something.bnf. Do not edit it manually.
package com.android.tools.idea.gradle.something.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder.*;
import com.android.tools.idea.gradle.something.psi.SomethingBlockMixin;
import com.android.tools.idea.gradle.something.psi.*;
import com.android.tools.idea.gradle.something.parser.PsiImplUtil;

public class SomethingBlockImpl extends SomethingBlockMixin implements SomethingBlock {

  public SomethingBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull SomethingVisitor visitor) {
    visitor.visitBlock(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof SomethingVisitor) accept((SomethingVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<SomethingAssignment> getAssignmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, SomethingAssignment.class);
  }

  @Override
  @NotNull
  public List<SomethingBlock> getBlockList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, SomethingBlock.class);
  }

  @Override
  @NotNull
  public List<SomethingFactory> getFactoryList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, SomethingFactory.class);
  }

  @Override
  @Nullable
  public SomethingIdentifier getIdentifier() {
    return findChildByClass(SomethingIdentifier.class);
  }

}
