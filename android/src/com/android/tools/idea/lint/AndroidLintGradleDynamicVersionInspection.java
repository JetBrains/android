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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintGradleDynamicVersionInspection extends AndroidLintInspectionBase {
  public AndroidLintGradleDynamicVersionInspection() {
    super(AndroidBundle.message("android.lint.inspections.gradle.dynamic.version"), GradleDetector.PLUS);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    GradleCoordinate plus = LintFix.getData(fixData, GradleCoordinate.class);
    if (plus != null && plus.getArtifactId() != null) {
      return new AndroidLintQuickFix[]{
        new ReplaceStringQuickFix("Replace with specific version", plus.getRevision(), "specific version") {
          @Nullable
          @Override
          protected String getNewValue() {
            return RepositoryUrlManager.get().resolveDynamicCoordinateVersion(plus, startElement.getProject());
          }
        }};
    }

    return super.getQuickFixes(startElement, endElement, message, fixData);
  }
}
