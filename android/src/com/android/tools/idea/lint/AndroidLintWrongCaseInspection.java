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

import com.android.tools.lint.checks.WrongCaseDetector;
import com.intellij.openapi.editor.Document;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.lint.detector.api.TextFormat.RAW;

public class AndroidLintWrongCaseInspection extends AndroidLintInspectionBase {
  public AndroidLintWrongCaseInspection() {
    super(AndroidBundle.message("android.lint.inspections.wrong.case"), WrongCaseDetector.WRONG_CASE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
    final String current = WrongCaseDetector.getOldValue(message, RAW);
    final String proposed = WrongCaseDetector.getNewValue(message, RAW);
    if (proposed != null && current != null) {
      return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(null, current, proposed) {
        @Override
        protected void editAfter(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
          String text = document.getText();
          int index = text.indexOf("</" + current + ">");
          if (index != -1) {
            document.replaceString(index + 2, index + 2 + current.length(), proposed);
          }
        }
      }};
    }

    return AndroidLintQuickFix.EMPTY_ARRAY;
  }
}
