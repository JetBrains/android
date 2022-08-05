// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecChinSizeParam;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecSizeT;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecVisitor;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class DeviceSpecChinSizeParamImpl extends DeviceSpecParamImpl implements DeviceSpecChinSizeParam {

  public DeviceSpecChinSizeParamImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitChinSizeParam(this);
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
