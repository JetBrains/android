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

public class AidlTypeArgumentsImpl extends AidlPsiCompositeElementImpl implements AidlTypeArguments {

  public AidlTypeArgumentsImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AidlVisitor) ((AidlVisitor)visitor).visitTypeArguments(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<AidlType> getTypeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AidlType.class);
  }

}
