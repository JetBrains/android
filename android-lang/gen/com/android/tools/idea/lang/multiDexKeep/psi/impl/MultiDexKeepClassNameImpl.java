// generatedFilesHeader.txt
package com.android.tools.idea.lang.multiDexKeep.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.multiDexKeep.psi.MultiDexKeepPsiTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.android.tools.idea.lang.multiDexKeep.psi.*;
import com.intellij.psi.PsiReference;

public class MultiDexKeepClassNameImpl extends ASTWrapperPsiElement implements MultiDexKeepClassName {

  public MultiDexKeepClassNameImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MultiDexKeepVisitor visitor) {
    visitor.visitClassName(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MultiDexKeepVisitor) accept((MultiDexKeepVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getString() {
    return findNotNullChildByType(STRING);
  }

  @Override
  public PsiReference getReference() {
    return PsiImplUtil.getReference(this);
  }

}
