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

import com.android.resources.ResourceUrl;
import com.android.tools.lint.checks.ManifestDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintMipmapIconsInspection extends AndroidLintInspectionBase {
  public AndroidLintMipmapIconsInspection() {
    super(AndroidBundle.message("android.lint.inspections.mipmap.icons"), ManifestDetector.MIPMAP);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    PsiElement parent = startElement.getParent();
    if (parent instanceof XmlAttribute) {
      XmlAttribute attribute = (XmlAttribute)parent;
      String value = attribute.getValue();
      if (value != null) {
        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && !url.framework) {
          return new AndroidLintQuickFix[]{new MigrateDrawableToMipmapFix(url)};
        }
      }
    }
    return super.getQuickFixes(startElement, endElement, message, fixData);
  }
}
