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

import com.android.tools.lint.checks.HardcodedValuesDetector;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidAddStringResourceQuickFix;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class AndroidLintHardcodedTextInspection extends AndroidLintInspectionBase {
  public AndroidLintHardcodedTextInspection() {
    super(AndroidBundle.message("android.lint.inspections.hardcoded.text"), HardcodedValuesDetector.ISSUE);
  }

  @NotNull
  @Override
  public IntentionAction[] getIntentions(@NotNull final PsiElement startElement, @NotNull PsiElement endElement) {
    return new IntentionAction[]{new AndroidAddStringResourceQuickFix(startElement)};
  }
}
