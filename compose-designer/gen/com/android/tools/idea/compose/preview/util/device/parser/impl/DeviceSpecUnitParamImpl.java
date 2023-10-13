// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.android.tools.idea.compose.preview.util.device.parser.*;

public class DeviceSpecUnitParamImpl extends DeviceSpecParamImpl implements DeviceSpecUnitParam {

  public DeviceSpecUnitParamImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitUnitParam(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public DeviceSpecUnit getUnit() {
    return findNotNullChildByClass(DeviceSpecUnit.class);
  }

}
