/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.error;

import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.HtmlBuilder;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

public class MockIssueFactory {

  @NotNull
  private static Issue createIssue() {
    return Issue.create("toto", "tata", "titi", Category.LINT, 10, Severity.ERROR, Mockito.mock(Implementation.class));
  }

  public static void addLintIssue(LintAnnotationsModel model, HighlightDisplayLevel level) {
    Issue issue = createIssue();
    AndroidLintInspectionBase inspection = new AndroidLintInspectionBase("Mock Issue", issue) {
    };

    NlComponent nlComponent = Mockito.mock(NlComponent.class);
    Mockito.when(nlComponent.getTagName()).thenReturn("MockTag");
    model.addIssue(nlComponent, issue, "",
                   inspection, level, Mockito.mock(PsiElement.class), Mockito.mock(PsiElement.class),
                   null);
  }

  public static RenderErrorModel.Issue createRenderIssue(HighlightSeverity level) {
    return createRenderIssue(level, null, null);
  }

  public static RenderErrorModel.Issue createRenderIssue(HighlightSeverity level, String title, String content) {
    RenderErrorModel.Issue.Builder builder = RenderErrorModel.Issue.builder().setSeverity(level);
    if (title != null) builder.setSummary(title);
    if (content != null) builder.setHtmlContent(new HtmlBuilder().add(content));
    return builder.build();
  }
}
