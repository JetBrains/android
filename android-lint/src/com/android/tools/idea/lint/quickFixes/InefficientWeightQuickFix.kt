/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lint.quickFixes;

import com.android.SdkConstants;
import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class InefficientWeightQuickFix extends DefaultLintQuickFix {

  public InefficientWeightQuickFix() {
    super(AndroidLintBundle.message("android.lint.fix.replace.with.zero.dp"));
  }

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
}
