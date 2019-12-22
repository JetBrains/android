package org.jetbrains.android.inspections.lint;

import com.android.SdkConstants;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class InefficientWeightQuickFix implements LintIdeQuickFix {

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return;
    }

    final XmlTag parentTag = tag.getParentTag();
    if (parentTag == null) {
      return;
    }
    String attrName;

    if (SdkConstants.VALUE_VERTICAL
      .equals(parentTag.getAttributeValue(SdkConstants.ATTR_ORIENTATION, SdkConstants.ANDROID_URI))) {
      attrName = SdkConstants.ATTR_LAYOUT_HEIGHT;
    }
    else {
      attrName = SdkConstants.ATTR_LAYOUT_WIDTH;
    }
    tag.setAttribute(attrName, SdkConstants.ANDROID_URI, "0dp");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return false;
    }
    return tag.getParentTag() != null;
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.lint.fix.replace.with.zero.dp");
  }
}
