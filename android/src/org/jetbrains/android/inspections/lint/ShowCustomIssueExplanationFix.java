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
package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.TextFormat;
import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**9
 * Quickfix for showing the full explanation for third party / custom lint issues
 */
class ShowCustomIssueExplanationFix implements AndroidLintQuickFix {
  private final PsiElement myElement;
  private final Issue myIssue;

  public static AndroidLintQuickFix[] getFixes(@NotNull PsiElement element, @NotNull String message) {
    Issue issue = IntellijLintClient.findCustomIssue(message);
    if (issue != null) {
      return new AndroidLintQuickFix[]{new ShowCustomIssueExplanationFix(element, issue)};
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }

  private ShowCustomIssueExplanationFix(@NotNull PsiElement element, @NotNull Issue issue) {
    myElement = element;
    myIssue = issue;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    Project project = myElement.getProject();
    DocumentationManager manager = DocumentationManager.getInstance(project);
    DocumentationComponent component = new DocumentationComponent(manager);
    component.setText("<html>" + myIssue.getExplanation(TextFormat.HTML) + "</html>", myElement, false);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
        .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(true)
        .createPopup();
    component.setHint(popup);
    if (context.getType() == AndroidQuickfixContexts.EditorContext.TYPE) {
      popup.showInBestPositionFor(((AndroidQuickfixContexts.EditorContext)context).getEditor());
    } else {
      popup.showCenteredInCurrentWindow(project);
    }
    Disposer.dispose(component);
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
    return "Show Full Explanation";
  }
}