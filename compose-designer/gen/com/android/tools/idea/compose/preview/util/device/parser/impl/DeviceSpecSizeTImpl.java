// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import static com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes.NUMERIC_T;

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecSizeT;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecUnit;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceSpecSizeTImpl extends ASTWrapperPsiElement implements DeviceSpecSizeT {

  public DeviceSpecSizeTImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitSizeT(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public DeviceSpecUnit getUnit() {
    return findChildByClass(DeviceSpecUnit.class);
  }

  @Override
  @NotNull
  public PsiElement getNumericT() {
    return findNotNullChildByType(NUMERIC_T);
  }

}
