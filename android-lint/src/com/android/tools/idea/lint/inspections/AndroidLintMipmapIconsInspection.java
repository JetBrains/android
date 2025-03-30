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
package com.android.tools.idea.lint.inspections;

import com.android.resources.ResourceUrl;
import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.quickFixes.MigrateDrawableToMipmapFix;
import com.android.tools.lint.checks.ManifestDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintMipmapIconsInspection extends AndroidLintInspectionBase {
  public AndroidLintMipmapIconsInspection() {
    super(AndroidLintBundle.message("android.lint.inspections.mipmap.icons"), ManifestDetector.MIPMAP);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    PsiElement parent = startElement.getParent();
    if (parent instanceof XmlAttribute) {
      XmlAttribute attribute = (XmlAttribute)parent;
      String value = attribute.getValue();
      if (value != null) {
        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && !url.isFramework()) {
          return new LintIdeQuickFix[]{new MigrateDrawableToMipmapFix(url)};
        }
      }
    }
    return super.getQuickFixes(startElement, endElement, message, fixData);
  }
}
