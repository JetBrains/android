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

import com.android.tools.lint.checks.UnusedResourceDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.inspections.lint.SetAttributeQuickFix;
import org.jetbrains.android.refactoring.UnusedResourcesQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.ATTR_KEEP;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.lint.detector.api.TextFormat.RAW;

public class AndroidLintUnusedResourcesInspection extends AndroidLintInspectionBase {
  public AndroidLintUnusedResourcesInspection() {
    super(AndroidBundle.message("android.lint.inspections.unused.resources"), UnusedResourceDetector.ISSUE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    String resource = LintFix.getData(fixData, String.class);
    if (resource != null) {
      String resourceUrl = "@" + resource.substring(2).replace('.', '/');
      return new AndroidLintQuickFix[]{
        new UnusedResourcesQuickFix(null),
        new UnusedResourcesQuickFix(resource),
        new SetAttributeQuickFix("Add a tools:keep attribute to mark as implicitly used", ATTR_KEEP, TOOLS_URI, resourceUrl) {
          @Override
          public boolean startInWriteAction() {
            // The unused resource quickfixes are refactoring operations, and refactoring operations
            // cannot be initiated under a write lock (this is enforced in BaseRefactoringProcessor#run.)
            // However, PerformFixesModalTask#iteration looks at *all* the fixes (across different families)
            // when deciding whether to run the fix in the write thread. Therefore, even though
            // UnusedResourcesQuickFix#startInWriteAction returns false, the presence of this SetAttributeQuickFix
            // which normally returns true from startInWriteAction causes the UnusedResourcesQuickFix to be run
            // under a write lock, which then crashes.
            //
            // Instead, we mark *all* the actions as not starting in a write thread, and then we manually
            // run the SetAttributeQuickFix in a write action by wrapping the apply call below.
            return false;
          }

          @Override
          public void apply(@NotNull PsiElement startElement,
                            @NotNull PsiElement endElement,
                            @NotNull AndroidQuickfixContexts.Context context) {
            WriteCommandAction.runWriteCommandAction(startElement.getProject(), getName(), null,
                                                 () -> super.apply(startElement, endElement, context), startElement.getContainingFile());
          }
        }
      };
    }
    else {
      return new AndroidLintQuickFix[]{new UnusedResourcesQuickFix(null)};
    }
  }
}
