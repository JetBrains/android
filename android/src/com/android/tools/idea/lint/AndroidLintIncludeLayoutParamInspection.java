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

import com.android.tools.lint.checks.IncludeDetector;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.SetAttributeQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;

public class AndroidLintIncludeLayoutParamInspection extends AndroidLintInspectionBase {
  public AndroidLintIncludeLayoutParamInspection() {
    super(AndroidBundle.message("android.lint.inspections.include.layout.param"), IncludeDetector.ISSUE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable Object extraData) {
    List<AndroidLintQuickFix> fixes = Lists.newArrayListWithExpectedSize(2);
    if (extraData instanceof List) {
      @SuppressWarnings("unchecked")
      List<String> missing = (List<String>) extraData;
      if (missing.contains(ATTR_LAYOUT_WIDTH)) {
        fixes.add(new SetAttributeQuickFix("Set layout_width", ATTR_LAYOUT_WIDTH, null));
      }
      if (missing.contains(ATTR_LAYOUT_HEIGHT)) {
        fixes.add(new SetAttributeQuickFix("Set layout_height", ATTR_LAYOUT_HEIGHT, null));
      }
    }
    return fixes.toArray(new AndroidLintQuickFix[fixes.size()]);
  }
}
