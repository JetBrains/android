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

import com.android.tools.lint.checks.TypoDetector;
import com.google.common.collect.Lists;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

import static com.android.tools.lint.detector.api.TextFormat.RAW;

public class AndroidLintTyposInspection extends AndroidLintInspectionBase {
  public AndroidLintTyposInspection() {
    super(AndroidBundle.message("android.lint.inspections.typos"), TypoDetector.ISSUE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
    TypoDetector.TypoSuggestionInfo info = TypoDetector.getSuggestions(message, RAW);
    final List<String> suggestions = info.getReplacements();
    if (!suggestions.isEmpty()) {
      List<AndroidLintQuickFix> fixes = Lists.newArrayListWithExpectedSize(suggestions.size());
      final String originalPattern = '(' + Pattern.quote(info.getOriginal()) + ')';
      for (String suggestion : suggestions) {
        fixes.add(new ReplaceStringQuickFix("Replace with \"" + suggestion + "\"", originalPattern, suggestion));
      }
      return fixes.toArray(new AndroidLintQuickFix[fixes.size()]);
    }

    return AndroidLintQuickFix.EMPTY_ARRAY;
  }
}
