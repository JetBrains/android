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

import com.android.tools.lint.checks.AppIndexingApiDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.SetAttributeQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.ATTR_HOST;
import static com.android.SdkConstants.ATTR_SCHEME;

public class AndroidLintGoogleAppIndexingApiWarningInspection extends AndroidLintInspectionBase {
  public AndroidLintGoogleAppIndexingApiWarningInspection() {
    super(AndroidBundle.message("android.lint.inspections.google.app.indexing.api.warning"), AppIndexingApiDetector.ISSUE_APP_INDEXING_API);
  }

  @NotNull
  static AndroidLintQuickFix[] getAppIndexingQuickFix(PsiElement startElement, String message) {
    AppIndexingApiDetector.IssueType type = AppIndexingApiDetector.IssueType.parse(message);
    switch (type) {
      case SCHEME_MISSING:
      case URL_MISSING:
        return new AndroidLintQuickFix[]{new SetAttributeQuickFix("Set scheme", ATTR_SCHEME, "http")};
      case HOST_MISSING:
        return new AndroidLintQuickFix[]{new SetAttributeQuickFix("Set host", ATTR_HOST, null)};
      case MISSING_SLASH:
        PsiElement parent = startElement.getParent();
        if (parent instanceof XmlAttribute) {
          XmlAttribute attr = (XmlAttribute)parent;
          String path = attr.getValue();
          if (path != null) {
            return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Replace with /" + path, path, "/" + path)};
          }
        }
        break;
      default:
        break;
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    return getAppIndexingQuickFix(startElement, message);
  }
}
