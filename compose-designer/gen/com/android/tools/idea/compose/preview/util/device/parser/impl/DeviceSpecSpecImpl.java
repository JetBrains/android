// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecParam;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecSpec;
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecVisitor;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class DeviceSpecSpecImpl extends ASTWrapperPsiElement implements DeviceSpecSpec {

  public DeviceSpecSpecImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitSpec(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<DeviceSpecParam> getParamList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, DeviceSpecParam.class);
  }

}
