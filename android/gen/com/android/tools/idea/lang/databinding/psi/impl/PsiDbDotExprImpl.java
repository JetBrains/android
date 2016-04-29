// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.databinding.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.*;
import com.android.tools.idea.lang.databinding.psi.*;

public class PsiDbDotExprImpl extends PsiDbExprImpl implements PsiDbDotExpr {

  public PsiDbDotExprImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof PsiDbVisitor) ((PsiDbVisitor)visitor).visitDotExpr(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiDbExpr getExpr() {
    return findNotNullChildByClass(PsiDbExpr.class);
  }

  @Override
  @NotNull
  public PsiDbFieldName getFieldName() {
    return findNotNullChildByClass(PsiDbFieldName.class);
  }

}
