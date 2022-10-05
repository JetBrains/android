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
package com.android.tools.idea.common.lint;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class IssueDataTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  NlComponent myComponent;

  @Mock
  AndroidLintInspectionBase myInspection;

  @Mock
  HighlightDisplayLevel myHighlightLevel;

  @Mock
  SmartPsiElementPointer<PsiElement> myPsiPointer;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void compareTo() {
    LintAnnotationsModel model = new LintAnnotationsModel();
    addIssue(model, HighlightDisplayLevel.ERROR, 5, Severity.ERROR);
    addIssue(model, HighlightDisplayLevel.WARNING, 5, Severity.ERROR);
    List<LintAnnotationsModel.IssueData> issues = model.getIssues();
    Assert.assertTrue(issues.get(0).compareTo(issues.get(1)) > 0);
    issues.clear();

    addIssue(model, HighlightDisplayLevel.ERROR, 5, Severity.ERROR);
    addIssue(model, HighlightDisplayLevel.ERROR, 5, Severity.ERROR);
    Assert.assertTrue(issues.get(0).compareTo(issues.get(1)) == 0);
    Assert.assertTrue(issues.get(0).compareTo(issues.get(0)) == 0);
    issues.clear();

    addIssue(model, HighlightDisplayLevel.ERROR, 4, Severity.ERROR);
    addIssue(model, HighlightDisplayLevel.ERROR, 5, Severity.WARNING);
    Assert.assertTrue(issues.get(0).compareTo(issues.get(1)) < 0);
    issues.clear();

    addIssue(model, HighlightDisplayLevel.ERROR, 5, Severity.WARNING);
    addIssue(model, HighlightDisplayLevel.ERROR, 5, Severity.ERROR);
    Assert.assertTrue(issues.get(0).compareTo(issues.get(1)) < 0);
    issues.clear();
  }

  @NotNull
  private static Issue createIssue(int priority, Severity severity) {
    Implementation implementation = Mockito.mock(Implementation.class);
    Mockito.when(implementation.getScope()).thenReturn(Scope.RESOURCE_FILE_SCOPE);
    return Issue.create("toto", "tata", "titi", Category.LINT, priority, severity, implementation);
  }

  void addIssue(LintAnnotationsModel model, HighlightDisplayLevel level, int priority, Severity severity) {
    model.addIssue(myComponent, null, createIssue(priority, severity), "", myInspection, level, myPsiPointer, myPsiPointer, null);
  }
}