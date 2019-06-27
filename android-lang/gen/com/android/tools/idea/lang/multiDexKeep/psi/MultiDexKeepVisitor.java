// generatedFilesHeader.txt
package com.android.tools.idea.lang.multiDexKeep.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class MultiDexKeepVisitor extends PsiElementVisitor {

  public void visitClassName(@NotNull MultiDexKeepClassName o) {
    visitPsiElement(o);
  }

  public void visitClassNames(@NotNull MultiDexKeepClassNames o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
