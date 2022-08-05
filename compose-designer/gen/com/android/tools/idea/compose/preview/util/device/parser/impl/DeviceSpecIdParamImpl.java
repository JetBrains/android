// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import static com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes.STRING_T;

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecIdParam;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecVisitor;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class DeviceSpecIdParamImpl extends DeviceSpecParamImpl implements DeviceSpecIdParam {

  public DeviceSpecIdParamImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitIdParam(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getStringT() {
    return findNotNullChildByType(STRING_T);
  }

}
