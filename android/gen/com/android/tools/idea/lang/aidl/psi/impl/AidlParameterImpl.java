// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.aidl.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.aidl.lexer.AidlTokenTypes.*;
import com.android.tools.idea.lang.aidl.psi.*;

public class AidlParameterImpl extends AidlPsiCompositeElementImpl implements AidlParameter {

  public AidlParameterImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AidlVisitor) ((AidlVisitor)visitor).visitParameter(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public AidlDirection getDirection() {
    return findChildByClass(AidlDirection.class);
  }

  @Override
  @NotNull
  public AidlType getType() {
    return findNotNullChildByClass(AidlType.class);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

}
