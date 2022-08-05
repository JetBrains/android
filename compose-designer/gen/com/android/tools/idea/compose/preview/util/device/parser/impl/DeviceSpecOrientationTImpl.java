// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecOrientationT;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class DeviceSpecOrientationTImpl extends ASTWrapperPsiElement implements DeviceSpecOrientationT {

  public DeviceSpecOrientationTImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitOrientationT(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

}
