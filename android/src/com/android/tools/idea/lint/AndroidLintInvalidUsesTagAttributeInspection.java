/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.common.ReplaceStringQuickFix;
import com.android.tools.lint.checks.AndroidAutoDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import java.util.List;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintInvalidUsesTagAttributeInspection extends AndroidLintInspectionBase {
  public AndroidLintInvalidUsesTagAttributeInspection() {
    super(AndroidBundle.message("android.lint.inspections.invalid.uses.tag.attribute"), AndroidAutoDetector.INVALID_USES_TAG_ISSUE);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message,
                                         @Nullable LintFix fixData) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class);
    XmlAttributeValue attributeValue = attribute == null ? null : attribute.getValueElement();
    if (attributeValue != null && attributeValue.getTextLength() != 0) {
      String value = StringUtil.unquoteString(attributeValue.getText());
      String regexp = "(" + value + ")";
      String[] suggestions = AndroidAutoDetector.getAllowedAutomotiveAppTypes();
      List<LintIdeQuickFix> fixes = Lists.newArrayListWithExpectedSize(suggestions.length);
      for (String suggestion : suggestions) {
        fixes.add(new ReplaceStringQuickFix("Replace with \"" + suggestion + "\"", null, regexp, suggestion));
      }
      return fixes.toArray(LintIdeQuickFix.EMPTY_ARRAY);
    }
    else {
      return super.getQuickFixes(startElement, endElement, message, fixData);
    }
  }
}
