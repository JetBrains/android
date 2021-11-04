// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.logcat.filters.parser;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class LogcatFilterVisitor extends PsiElementVisitor {

  public void visitAndExpression(@NotNull LogcatFilterAndExpression o) {
    visitExpression(o);
  }

  public void visitExpression(@NotNull LogcatFilterExpression o) {
    visitPsiElement(o);
  }

  public void visitLiteralExpression(@NotNull LogcatFilterLiteralExpression o) {
    visitExpression(o);
  }

  public void visitOrExpression(@NotNull LogcatFilterOrExpression o) {
    visitExpression(o);
  }

  public void visitParenExpression(@NotNull LogcatFilterParenExpression o) {
    visitExpression(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
