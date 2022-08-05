// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecBooleanT;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class DeviceSpecBooleanTImpl extends ASTWrapperPsiElement implements DeviceSpecBooleanT {

  public DeviceSpecBooleanTImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitBooleanT(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

}
