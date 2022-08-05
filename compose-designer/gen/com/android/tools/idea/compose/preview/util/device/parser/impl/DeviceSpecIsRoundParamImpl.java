// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecBooleanT;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecIsRoundParam;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecVisitor;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class DeviceSpecIsRoundParamImpl extends DeviceSpecParamImpl implements DeviceSpecIsRoundParam {

  public DeviceSpecIsRoundParamImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitIsRoundParam(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public DeviceSpecBooleanT getBooleanT() {
    return findNotNullChildByClass(DeviceSpecBooleanT.class);
  }

}
