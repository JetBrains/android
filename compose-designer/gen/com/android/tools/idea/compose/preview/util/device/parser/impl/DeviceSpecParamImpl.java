// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.android.tools.idea.compose.preview.util.device.parser.*;

public class DeviceSpecParamImpl extends ASTWrapperPsiElement implements DeviceSpecParam {

  public DeviceSpecParamImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitParam(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public DeviceSpecChinSizeParam getChinSizeParam() {
    return findChildByClass(DeviceSpecChinSizeParam.class);
  }

  @Override
  @Nullable
  public DeviceSpecDpiParam getDpiParam() {
    return findChildByClass(DeviceSpecDpiParam.class);
  }

  @Override
  @Nullable
  public DeviceSpecHeightParam getHeightParam() {
    return findChildByClass(DeviceSpecHeightParam.class);
  }

  @Override
  @Nullable
  public DeviceSpecIsRoundParam getIsRoundParam() {
    return findChildByClass(DeviceSpecIsRoundParam.class);
  }

  @Override
  @Nullable
  public DeviceSpecNameParam getNameParam() {
    return findChildByClass(DeviceSpecNameParam.class);
  }

  @Override
  @Nullable
  public DeviceSpecOrientationParam getOrientationParam() {
    return findChildByClass(DeviceSpecOrientationParam.class);
  }

  @Override
  @Nullable
  public DeviceSpecParentParam getParentParam() {
    return findChildByClass(DeviceSpecParentParam.class);
  }

  @Override
  @Nullable
  public DeviceSpecWidthParam getWidthParam() {
    return findChildByClass(DeviceSpecWidthParam.class);
  }

}
