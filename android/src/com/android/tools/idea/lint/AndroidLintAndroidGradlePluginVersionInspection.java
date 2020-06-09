/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.common.LintIdeSupport;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.Arrays;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintAndroidGradlePluginVersionInspection extends AndroidLintInspectionBase {
  public AndroidLintAndroidGradlePluginVersionInspection() {
    super(AndroidBundle.message("android.lint.inspections.android.gradle.plugin.version"), GradleDetector.AGP_DEPENDENCY);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    ArrayList<LintIdeQuickFix> quickFixes = new ArrayList<>();
    // find and add a quick fix corresponding to a "safe" (micro-level change only) AGP upgrade
    if (fixData instanceof LintFix.LintFixGroup && ((LintFix.LintFixGroup)fixData).type == LintFix.GroupType.ALTERNATIVES) {
      for (LintFix fix : ((LintFix.LintFixGroup)fixData).fixes) {
        if (fix.robot) {
          quickFixes.addAll(Arrays.asList(super.getQuickFixes(startElement, endElement, message, fix)));
        }
      }
    }
    else if (fixData != null && fixData.robot) {
      quickFixes.addAll(Arrays.asList(super.getQuickFixes(startElement, endElement, message, fixData)));
    }

    if (LintIdeSupport.get().shouldRecommendUpdateAgpToLatest(startElement.getProject())) {
      GradleVersion recommendedVersion = LintIdeSupport.get().recommendedAgpVersion(startElement.getProject());
      LintIdeQuickFix auaQuickFix = new InvokeAGPUpgradeAssistantQuickFix(recommendedVersion);
      quickFixes.add(auaQuickFix);
    }
    return quickFixes.toArray(LintIdeQuickFix.EMPTY_ARRAY);
  }

  public static class InvokeAGPUpgradeAssistantQuickFix implements LintIdeQuickFix {
    private final GradleVersion agpVersion;

    public InvokeAGPUpgradeAssistantQuickFix(@Nullable GradleVersion agpVersion) {
      super();
      this.agpVersion = agpVersion;
    }

    @Override
    public void apply(@NotNull PsiElement startElement,
                      @NotNull PsiElement endElement,
                      @NotNull AndroidQuickfixContexts.Context context) {
      LintIdeSupport.get().updateAgpToLatest(startElement.getProject());
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
      if (agpVersion == null) {
        return "Invoke Upgrade Assistant";
      }
      else {
        return "Invoke Upgrade Assistant for upgrade to " + agpVersion.toString();
      }
    }
  }
}
