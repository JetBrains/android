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

import com.android.tools.lint.checks.RtlDetector;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.SetAttributeQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidLintRtlCompatInspection extends AndroidLintInspectionBase {
  private static final Pattern QUOTED_PARAMETER = Pattern.compile("`.+:(.+)=\"(.*)\"`");

  public AndroidLintRtlCompatInspection() {
    super(AndroidBundle.message("android.lint.inspections.rtl.compat"), RtlDetector.COMPAT);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
    if (message.startsWith("To support older versions than API 17")) {
      Matcher matcher = QUOTED_PARAMETER.matcher(message);
      if (matcher.find()) {
        final String name = matcher.group(1);
        final String value = matcher.group(2);
        return new AndroidLintQuickFix[]{new SetAttributeQuickFix(String.format("Set %s", name), name, value)};
      }
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }

}
