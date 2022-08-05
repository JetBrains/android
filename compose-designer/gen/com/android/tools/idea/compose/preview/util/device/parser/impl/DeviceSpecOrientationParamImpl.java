// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecOrientationParam;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecOrientationT;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecVisitor;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class DeviceSpecOrientationParamImpl extends DeviceSpecParamImpl implements DeviceSpecOrientationParam {

  public DeviceSpecOrientationParamImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitOrientationParam(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public DeviceSpecOrientationT getOrientationT() {
    return findNotNullChildByClass(DeviceSpecOrientationT.class);
  }

}
