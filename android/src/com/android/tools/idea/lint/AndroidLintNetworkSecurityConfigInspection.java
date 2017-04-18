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

import com.android.tools.lint.checks.NetworkSecurityConfigDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.RenameAttributeQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AndroidLintNetworkSecurityConfigInspection extends AndroidLintInspectionBase {
  public AndroidLintNetworkSecurityConfigInspection() {
    super(AndroidBundle.message("android.lint.inspections.network.security.config"), NetworkSecurityConfigDetector.ISSUE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    if (NetworkSecurityConfigDetector.isAttributeSpellingError(message)) {
      XmlTag parentTag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
      XmlAttribute currentAttr = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class, false);
      assert parentTag != null;
      assert currentAttr != null;
      List<String> suggestions =
        NetworkSecurityConfigDetector.getAttributeSpellingSuggestions(currentAttr.getName(), parentTag.getName());
      AndroidLintQuickFix[] attrFixes = new AndroidLintQuickFix[suggestions.size()];
      for (int i = 0; i < attrFixes.length; i++) {
        attrFixes[i] = new RenameAttributeQuickFix(null /* no namespace */, suggestions.get(i));
      }
      return attrFixes;
    }
    else if (NetworkSecurityConfigDetector.isTagSpellingError(message)) {
      XmlTag currentTag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
      assert currentTag != null;
      XmlTag parentTag = currentTag.getParentTag();
      assert parentTag != null;
      List<String> suggestions =
        NetworkSecurityConfigDetector.getTagSpellingSuggestions(currentTag.getName(), parentTag.getName());
      AndroidLintQuickFix[] elementQuickFixes = new AndroidLintQuickFix[suggestions.size()];
      for (int i = 0; i < elementQuickFixes.length; i++) {
        elementQuickFixes[i] = new RenameXmlTagQuickFix(suggestions.get(i));
      }
      return elementQuickFixes;
    }
    else {
      return super.getQuickFixes(startElement, endElement, message, fixData);
    }
  }
}
