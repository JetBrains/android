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
package com.android.tools.idea.lint.inspections;

import static com.android.SdkConstants.ATTR_KEEP;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.common.SetAttributeQuickFix;
import com.android.tools.idea.lint.quickFixes.UnusedResourcesQuickFix;
import com.android.tools.lint.checks.UnusedResourceDetector;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class AndroidLintUnusedResourcesInspection extends AndroidLintInspectionBase {
  public AndroidLintUnusedResourcesInspection() {
    super(AndroidLintBundle.message("android.lint.inspections.unused.resources"), UnusedResourceDetector.ISSUE);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull Incident incident) {
    LintFix fixData = incident.getFix();
    String resource = LintFix.getString(fixData, UnusedResourceDetector.KEY_RESOURCE_FIELD, null);
    if (resource != null) {
      String resourceUrl = "@" + resource.substring(2).replace('.', '/');
      return new LintIdeQuickFix[]{
        new UnusedResourcesQuickFix(null),
        new UnusedResourcesQuickFix(resource),
        new SetAttributeQuickFix("Add a tools:keep attribute to mark as implicitly used", null, ATTR_KEEP, TOOLS_URI, resourceUrl) {
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
                                                     () -> super.apply(startElement, endElement, context),
                                                     startElement.getContainingFile());
          }
        }
      };
    }
    else {
      return new LintIdeQuickFix[]{new UnusedResourcesQuickFix(null)};
    }
  }
}
