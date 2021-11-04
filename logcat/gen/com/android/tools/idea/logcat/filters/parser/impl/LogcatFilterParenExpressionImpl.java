// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.logcat.filters.parser.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.*;
import com.android.tools.idea.logcat.filters.parser.*;

public class LogcatFilterParenExpressionImpl extends LogcatFilterExpressionImpl implements LogcatFilterParenExpression {

  public LogcatFilterParenExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull LogcatFilterVisitor visitor) {
    visitor.visitParenExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LogcatFilterVisitor) accept((LogcatFilterVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LogcatFilterExpression getExpression() {
    return findChildByClass(LogcatFilterExpression.class);
  }

}
