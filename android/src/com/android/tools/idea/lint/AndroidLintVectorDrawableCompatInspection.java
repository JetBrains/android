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

import com.android.tools.lint.checks.VectorDrawableCompatDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.RenameAttributeQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.*;

public class AndroidLintVectorDrawableCompatInspection extends AndroidLintInspectionBase {
  public AndroidLintVectorDrawableCompatInspection() {
    super(AndroidBundle.message("android.lint.inspections.vector.drawable.compat"), VectorDrawableCompatDetector.ISSUE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    XmlAttribute attribute = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class, false);
    if (attribute != null && ATTR_SRC.equals(attribute.getLocalName())) {
      return new AndroidLintQuickFix[]{new RenameAttributeQuickFix(AUTO_URI, ATTR_SRC_COMPAT)};
    }
    else {
      return super.getQuickFixes(startElement, endElement, message, fixData);
    }
  }
}
