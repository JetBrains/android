// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecSizeT;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecVisitor;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecWidthParam;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class DeviceSpecWidthParamImpl extends DeviceSpecParamImpl implements DeviceSpecWidthParam {

  public DeviceSpecWidthParamImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitWidthParam(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public DeviceSpecSizeT getSizeT() {
    return findNotNullChildByClass(DeviceSpecSizeT.class);
  }

}
