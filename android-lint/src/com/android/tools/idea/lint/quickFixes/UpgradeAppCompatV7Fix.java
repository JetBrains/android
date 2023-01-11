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
package com.android.tools.idea.lint.quickFixes;

import static com.android.tools.lint.checks.FontDetector.MIN_APPSUPPORT_VERSION;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.android.tools.idea.lint.common.LintIdeSupport;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Quickfix which updates the appcompat-v7 support library to the latest version
 * which is the minimum for downloadable font support
 * (and also installs it in the local maven repository if necessary)
 */
public class UpgradeAppCompatV7Fix extends DefaultLintQuickFix {
  public UpgradeAppCompatV7Fix() {
    super("Upgrade appcompat-v7 to recommended version");
  }

  @Override
  public void apply(@NotNull PsiElement startElement,
                    @NotNull PsiElement endElement,
                    @NotNull AndroidQuickfixContexts.Context context) {
    Module module = AndroidPsiUtils.getModuleSafely(startElement);
    apply(module);
  }

  public static void apply(@Nullable Module module) {
    if (module != null) {
      LintIdeSupport.get().updateToLatest(module, MIN_APPSUPPORT_VERSION);
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return super.isApplicable(startElement, endElement, contextType);
  }
}
