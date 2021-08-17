/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.error;

import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mockito;

public class IssuePanelTest extends AndroidTestCase {

  public void testPanel() {
    IssueModel model = new IssueModel();
    IssuePanel panel = new IssuePanel(LayoutTestUtilities.createSurface(DesignSurface.class), model);
    assertEquals("No issues", panel.getTitleText());
    LintAnnotationsModel lintAnnotationsModel = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR);
    model.addIssueProvider(new LintIssueProvider(lintAnnotationsModel));
    assertEquals("1 Error", panel.getTitleText());
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR);
    model.updateErrorsList();
    assertEquals("2 Errors", panel.getTitleText());
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.WARNING);
    model.updateErrorsList();
    assertEquals("1 Warning 2 Errors", panel.getTitleText());
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.WARNING);
    model.updateErrorsList();
    assertEquals("2 Warnings 2 Errors", panel.getTitleText());
  }

  public void testRemoveOldError() {
    IssueModel model = new IssueModel();
    IssuePanel panel = new IssuePanel(LayoutTestUtilities.createSurface(DesignSurface.class), model);
    assertEquals("No issues", panel.getTitleText());
    LintAnnotationsModel lintAnnotationsModel = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR);
    LintIssueProvider provider = new LintIssueProvider(lintAnnotationsModel);
    model.addIssueProvider(provider);
    assertEquals("1 Error", panel.getTitleText());
    panel.setSelectedIssue(panel.getIssueViews().get(0));
    assertNotNull(panel.getSelectedIssue());
    model.removeIssueProvider(provider);
    assertNull(panel.getSelectedIssue());
    LintAnnotationsModel newModel = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(newModel, HighlightDisplayLevel.WARNING);
    model.addIssueProvider(new LintIssueProvider(newModel));
    assertEquals("1 Warning", panel.getTitleText().trim());
  }

  /**
   * Creates two similar issues where just the psi elements are different and check that the
   * issue panel is updated when the first issue is replace by the other one
   *<p>
   * The bug associated with this test was caused because when editing a part of a component's xml (thus the psi)
   * that was not affecting the issue details, the issueview was not updated and the fix action was still pointing to the old psi
   * which was not valid anymore and had no parent.
   *
   * b/68236469
   */
  public void testRemoveIfPsiChangedError() {
    IssueModel model = new IssueModel();
    IssuePanel panel = new IssuePanel(LayoutTestUtilities.createSurface(DesignSurface.class), model);
    assertEquals("No issues", panel.getTitleText());
    LintAnnotationsModel lintAnnotationsModel = new LintAnnotationsModel();
    NlComponent source = Mockito.mock(NlComponent.class);
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR, source);
    LintIssueProvider provider = new LintIssueProvider(lintAnnotationsModel);
    model.addIssueProvider(provider);
    IssueView issueView = panel.getIssueViews().get(0);
    LintAnnotationsModel newModel = new LintAnnotationsModel();
    model.removeIssueProvider(provider);

    // When we create a new lint issue, the PSI elements are not the same, so the IssueView should also have changed
    MockIssueFactory.addLintIssue(newModel, HighlightDisplayLevel.ERROR, source);
    model.addIssueProvider(new LintIssueProvider(newModel));
    IssueView issueView2 = panel.getIssueViews().get(0);
    assertNotSame(issueView, issueView2);
  }
}