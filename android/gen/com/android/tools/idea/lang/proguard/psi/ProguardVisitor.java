// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.proguard.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class ProguardVisitor extends PsiElementVisitor {

  public void visitComment(@NotNull ProguardComment o) {
    visitPsiElement(o);
  }

  public void visitFlag(@NotNull ProguardFlag o) {
    visitPsiElement(o);
  }

  public void visitJavaSection(@NotNull ProguardJavaSection o) {
    visitPsiElement(o);
  }

  public void visitMultiLineFlag(@NotNull ProguardMultiLineFlag o) {
    visitPsiElement(o);
  }

  public void visitSingleLineFlag(@NotNull ProguardSingleLineFlag o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
