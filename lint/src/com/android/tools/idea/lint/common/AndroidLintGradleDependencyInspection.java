
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
package com.android.tools.idea.lint.common;

import static com.android.tools.lint.checks.ConstraintLayoutDetector.LATEST_KNOWN_VERSION;

import com.android.tools.lint.checks.ConstraintLayoutDetector;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintGradleDependencyInspection extends AndroidLintInspectionBase {
  public AndroidLintGradleDependencyInspection() {
    super(LintBundle.message("android.lint.inspections.gradle.dependency"), GradleDetector.DEPENDENCY);
  }

  public static void upgrade(@Nullable Module module) {
    if (module != null) {
      LintIdeSupport.get().updateToLatest(module, LATEST_KNOWN_VERSION);
    }
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    Class<?> detector = LintFix.getData(fixData, Class.class);
    if (Objects.equals(detector, ConstraintLayoutDetector.class)) {
      // Is this an upgrade message from the ConstraintLayoutDetector instead?
      return new LintIdeQuickFix[]{new UpgradeConstraintLayoutFix()};
    }
    return super.getQuickFixes(startElement, endElement, message, fixData);
  }

  public static class UpgradeConstraintLayoutFix implements LintIdeQuickFix {
    @Override
    public void apply(@NotNull PsiElement startElement,
                      @NotNull PsiElement endElement,
                      @NotNull AndroidQuickfixContexts.Context context) {
      Module module = ModuleUtilCore.findModuleForPsiElement(startElement);
      upgrade(module);
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement startElement,
                                @NotNull PsiElement endElement,
                                @NotNull AndroidQuickfixContexts.ContextType contextType) {
      return true;
    }

    @NotNull
    @Override
    public String getName() {
      return "Upgrade to recommended version";
    }
  }
}
