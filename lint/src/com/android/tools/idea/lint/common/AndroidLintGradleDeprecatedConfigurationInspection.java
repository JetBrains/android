/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class AndroidLintGradleDeprecatedConfigurationInspection extends AndroidLintInspectionBase {
  public AndroidLintGradleDeprecatedConfigurationInspection() {
    super(LintBundle.message("android.lint.inspections.gradle.deprecated.configuration"), GradleDetector.DEPRECATED_CONFIGURATION);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    LintIdeQuickFix[] quickFixes = super.getQuickFixes(startElement, endElement, message, fixData);
    if (!LintIdeSupport.get().shouldOfferUpgradeAssistantForDeprecatedConfigurations(startElement.getProject())) {
      return quickFixes;
    }
    return Stream.concat(Arrays.stream(quickFixes), Stream.of(new InvokeAGPUpgradeAssistantQuickFix())).toArray(LintIdeQuickFix[]::new);
  }

  static class InvokeAGPUpgradeAssistantQuickFix extends DefaultLintQuickFix {
    public InvokeAGPUpgradeAssistantQuickFix() {
      super("Invoke AGP Upgrade Assistant on deprecated configurations");
    }

    @Override
    public void apply(@NotNull PsiElement startElement,
                      @NotNull PsiElement endElement,
                      @NotNull AndroidQuickfixContexts.Context context) {
      LintIdeSupport.get().updateDeprecatedConfigurations(startElement.getProject(), startElement);
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement startElement,
                                @NotNull PsiElement endElement,
                                @NotNull AndroidQuickfixContexts.ContextType contextType) {
      return super.isApplicable(startElement, endElement, contextType);
    }
  }
}
