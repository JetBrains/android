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

public class AidlBodyImpl extends AidlPsiCompositeElementImpl implements AidlBody {

  public AidlBodyImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AidlVisitor) ((AidlVisitor)visitor).visitBody(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<AidlInterfaceDeclaration> getInterfaceDeclarationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AidlInterfaceDeclaration.class);
  }

  @Override
  @NotNull
  public List<AidlParcelableDeclaration> getParcelableDeclarationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AidlParcelableDeclaration.class);
  }

}
