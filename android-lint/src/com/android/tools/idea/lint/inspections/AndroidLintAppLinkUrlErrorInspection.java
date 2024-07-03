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

import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.common.LintIdeUtilsKt;
import com.android.tools.idea.lint.quickFixes.LaunchAppLinksAssistantFix;
import com.android.tools.lint.checks.AppLinksValidDetector;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.LintFix;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.psi.PsiElement;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class AndroidLintAppLinkUrlErrorInspection extends AndroidLintInspectionBase {
  public AndroidLintAppLinkUrlErrorInspection() {
    super(AndroidLintBundle.message("android.lint.inspections.app.link.url.error"), AppLinksValidDetector.VALIDATION);
  }

  @Override
  public @NotNull LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                                  @NotNull PsiElement endElement,
                                                  @NotNull Incident incident) {
    LintFix.DataMap data = LintIdeUtilsKt.getAndRemoveMapFix(incident);
    // Now that the DataMap has been removed, we can call the super method.
    LintIdeQuickFix[] quickFixesArray = super.getQuickFixes(startElement, endElement, incident);
    // Now insert the IDE-specific quick-fix, if needed.
    if (data != null && data.hasKey(AppLinksValidDetector.KEY_SHOW_APP_LINKS_ASSISTANT)) {
      List<LintIdeQuickFix> quickFixes = Lists.newArrayList(quickFixesArray);
      LaunchAppLinksAssistantFix fix = new LaunchAppLinksAssistantFix();
      fix.setPriority(PriorityAction.Priority.TOP);
      quickFixes.add(0, fix);
      quickFixesArray = quickFixes.toArray(LintIdeQuickFix.EMPTY_ARRAY);
    }
    return quickFixesArray;
  }
}
