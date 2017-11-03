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

import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.android.AndroidTestCase;

public class IssuePanelTest extends AndroidTestCase {

  public void testPanel() {
    IssueModel model = new IssueModel();
    IssuePanel panel = new IssuePanel(LayoutTestUtilities.createSurface(DesignSurface.class), model);
    assertEquals("No issues", panel.getTitleText());
    LintAnnotationsModel lintAnnotationsModel = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR);
    model.setLintAnnotationsModel(lintAnnotationsModel);
    assertEquals("1 Error", panel.getTitleText());
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR);
    model.setLintAnnotationsModel(lintAnnotationsModel);
    assertEquals("2 Errors", panel.getTitleText());
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.WARNING);
    model.setLintAnnotationsModel(lintAnnotationsModel);
    assertEquals("1 Warning 2 Errors", panel.getTitleText());
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.WARNING);
    model.setLintAnnotationsModel(lintAnnotationsModel);
    assertEquals("2 Warnings 2 Errors", panel.getTitleText());
  }

  public void testRemoveOldError() {
    IssueModel model = new IssueModel();
    IssuePanel panel = new IssuePanel(LayoutTestUtilities.createSurface(DesignSurface.class), model);
    assertEquals("No issues", panel.getTitleText());
    LintAnnotationsModel lintAnnotationsModel = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR);
    model.setLintAnnotationsModel(lintAnnotationsModel);
    assertEquals("1 Error", panel.getTitleText());
    LintAnnotationsModel newModel = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(newModel, HighlightDisplayLevel.WARNING);
    model.setLintAnnotationsModel(newModel);
    assertEquals("1 Warning", panel.getTitleText().trim());
  }
}