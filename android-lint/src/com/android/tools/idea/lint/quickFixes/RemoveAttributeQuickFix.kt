package com.android.tools.idea.lint.quickFixes;

import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

public class RemoveAttributeQuickFix extends DefaultLintQuickFix {
  public RemoveAttributeQuickFix() {
    super(AndroidLintBundle.message("android.lint.fix.remove.attribute"));
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class);
    if (attribute != null) {
      attribute.getParent().setAttribute(attribute.getName(), null);
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class) != null;
  }
}