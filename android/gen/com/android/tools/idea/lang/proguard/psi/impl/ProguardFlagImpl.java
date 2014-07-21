// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.proguard.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.proguard.psi.ProguardTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.android.tools.idea.lang.proguard.psi.*;

public class ProguardFlagImpl extends ASTWrapperPsiElement implements ProguardFlag {

  public ProguardFlagImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ProguardVisitor) ((ProguardVisitor)visitor).visitFlag(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ProguardComment getComment() {
    return findChildByClass(ProguardComment.class);
  }

  @Override
  @Nullable
  public ProguardMultiLineFlag getMultiLineFlag() {
    return findChildByClass(ProguardMultiLineFlag.class);
  }

  @Override
  @Nullable
  public ProguardSingleLineFlag getSingleLineFlag() {
    return findChildByClass(ProguardSingleLineFlag.class);
  }

}
