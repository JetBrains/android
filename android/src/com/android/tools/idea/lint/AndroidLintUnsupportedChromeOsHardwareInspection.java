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

import com.android.tools.lint.checks.ChromeOsDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.SetAttributeQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

public class AndroidLintUnsupportedChromeOsHardwareInspection extends AndroidLintInspectionBase {
  public AndroidLintUnsupportedChromeOsHardwareInspection() {
    super(AndroidBundle.message("android.lint.inspections.unsupported.chrome.os.hardware"), ChromeOsDetector.UNSUPPORTED_CHROME_OS_HARDWARE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    if (startElement.textMatches(PREFIX_ANDROID + ATTRIBUTE_REQUIRED)) {
      // android:required attribute
      XmlAttribute attr = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class);
      assert attr != null;
      return new AndroidLintQuickFix[]{
        new ReplaceStringQuickFix("Replace with required=\"false\"",
                                  null,
                                  PREFIX_ANDROID + ATTRIBUTE_REQUIRED + "=\"" + false + "\""),
      };
    }
    else if (startElement.textMatches(NODE_USES_FEATURE)) {
      return new AndroidLintQuickFix[]{new SetAttributeQuickFix("Set required=\"false\"", ATTRIBUTE_REQUIRED, VALUE_FALSE)};
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }
}
