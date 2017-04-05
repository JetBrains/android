/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.lint.checks.VectorPathDetector;
import com.android.tools.lint.detector.api.QuickfixData;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintInvalidVectorPathInspection extends AndroidLintInspectionBase {
  public AndroidLintInvalidVectorPathInspection() {
    super(AndroidBundle.message("android.lint.inspections.invalid.vector.path"), VectorPathDetector.PATH_VALID);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable Object extraData) {
    if (extraData instanceof QuickfixData) {
      QuickfixData data = (QuickfixData)extraData;
      ResourceUrl url = data.get(ResourceUrl.class);
      String number = data.get(String.class);
      if (url == null) {
        try {
          String converted = Double.toString(Double.parseDouble(number));
          if (!converted.contains("e") && !converted.contains("E")) { // Some numbers require it -- too large to format in other way
            return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Convert to plain float format", number, converted)};
          }
        }
        catch (Throwable ignore) {
        }
      } // otherwise you are editing something in a different file (e.g. a resource reference)
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }
}
