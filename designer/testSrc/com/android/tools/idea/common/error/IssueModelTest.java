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
package com.android.tools.idea.common.error;

import static com.android.tools.idea.common.error.MockIssueFactoryKt.createIssue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.uibuilder.error.RenderIssueProvider;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class IssueModelTest {

  @Rule
  public AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();

  private IssueModel myIssueModel;

  @Before
  public void setUp() throws Exception {
    myIssueModel = IssueModel.createForTesting(myProjectRule.getTestRootDisposable(), myProjectRule.getProject());
  }

  @Test
  public void setRenderErrorModel() {
    VirtualFile file = myProjectRule.getFixture().addFileToProject("res/layout/layout.xml", "").getVirtualFile();

    RenderErrorModel.Issue issue = MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR);
    RenderErrorModel renderErrorModel = createRenderErrorModel(issue);

    assertFalse(myIssueModel.hasIssues());
    assertFalse(hasRenderError());
    assertEquals(0, myIssueModel.getIssueCount());
    NlModel sourceNlModel = Mockito.mock(NlModel.class);
    Mockito.when(sourceNlModel.getVirtualFile()).thenReturn(file);
    myIssueModel.addIssueProvider(new RenderIssueProvider(sourceNlModel, renderErrorModel));
    assertTrue(myIssueModel.hasIssues());
    assertTrue(hasRenderError());
    assertEquals(1, myIssueModel.getIssueCount());
    assertArrayEquals(new Issue[]{RenderIssueProvider.NlRenderIssueWrapper.wrapIssue(issue, sourceNlModel)}, myIssueModel.getIssues().toArray());
  }

  @NotNull
  private static RenderErrorModel createRenderErrorModel(RenderErrorModel.Issue... issues) {
    return new RenderErrorModel(ImmutableList.copyOf(issues));
  }

  @Test
  public void setLintAnnotationModel() {

    LintAnnotationsModel lintAnnotationsModel = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR);

    assertFalse(myIssueModel.hasIssues());
    assertFalse(hasRenderError());
    assertEquals(0, myIssueModel.getIssueCount());
    myIssueModel.addIssueProvider(new LintIssueProvider(lintAnnotationsModel));
    assertTrue(myIssueModel.hasIssues());
    assertFalse(hasRenderError());
    assertTrue(myIssueModel.getIssueCount() == 1);
    assertArrayEquals(new Issue[]{new LintIssueProvider.LintIssueWrapper(lintAnnotationsModel.getIssues().get(0))},
                      myIssueModel.getIssues().toArray());
  }

  @Test
  public void listenerCalled() {
    final boolean[] listenerCalled = {false, false};
    IssueModel.IssueModelListener listener1 = () -> listenerCalled[0] = true;
    IssueModel.IssueModelListener listener2 = () -> listenerCalled[1] = true;
    myIssueModel.addErrorModelListener(listener1);
    myIssueModel.addErrorModelListener(listener2);
    myIssueModel.addIssueProvider(new RenderIssueProvider(null, createRenderErrorModel(
      MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR))));
    assertTrue(listenerCalled[0]);
    assertTrue(listenerCalled[1]);
    listenerCalled[0] = false;
    listenerCalled[1] = false;
    myIssueModel.removeErrorModelListener(listener1);
    myIssueModel.addIssueProvider(new RenderIssueProvider(null, createRenderErrorModel(
      MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR))));
    assertFalse(listenerCalled[0]);
    assertTrue(listenerCalled[1]);
  }

  @Test
  public void warningErrorCount() {
    assertFalse(myIssueModel.hasIssues());
    myIssueModel.addIssueProvider(new RenderIssueProvider(
      null,
      createRenderErrorModel(
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.WARNING))));
    assertEquals(3, myIssueModel.getIssueCount());
    assertEquals(2, myIssueModel.getErrorCount());
    assertEquals(1, myIssueModel.getWarningCount());
    LintAnnotationsModel model = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(model, HighlightDisplayLevel.ERROR);
    MockIssueFactory.addLintIssue(model, HighlightDisplayLevel.WARNING);
    MockIssueFactory.addLintIssue(model, HighlightDisplayLevel.ERROR);
    myIssueModel.addIssueProvider(new LintIssueProvider(model));
    assertEquals(6, myIssueModel.getIssueCount());
    assertEquals(4, myIssueModel.getErrorCount());
    assertEquals(2, myIssueModel.getWarningCount());
  }

  @Test
  public void testHighestSeverityIssue() {
    assertFalse(myIssueModel.hasIssues());
    NlComponent mockComponent = Mockito.mock(NlComponent.class, Mockito.RETURNS_DEEP_STUBS);
    Issue expectedHighest = createIssue(HighlightSeverity.WARNING, IssueSource.fromNlComponent(mockComponent));
    myIssueModel.addIssueProvider(
      new IssueProvider() {
        @Override
        public void collectIssues(@NotNull ImmutableCollection.Builder<Issue> issueListBuilder) {
          issueListBuilder.add(createIssue(HighlightSeverity.INFORMATION, IssueSource.fromNlComponent(mockComponent)));
          issueListBuilder.add(createIssue(HighlightSeverity.INFORMATION, IssueSource.fromNlComponent(mockComponent)));
          issueListBuilder.add(expectedHighest);
        }
    });
    assertEquals(3, myIssueModel.getIssueCount());
    Issue highest = myIssueModel.getHighestSeverityIssue(mockComponent);
    assertEquals(expectedHighest, highest );
  }

  @Test
  public void testIssueIconErrorInline() {
    HighlightSeverity severity = HighlightSeverity.ERROR;
    Icon icon = IssueModel.getIssueIcon(severity, false);
    assertNotNull(icon);
    assertEquals(StudioIcons.Common.ERROR_INLINE, icon);
  }

  @Test
  public void testIssueIconErrorInlineSelected() {
    HighlightSeverity severity = HighlightSeverity.ERROR;
    Icon icon = IssueModel.getIssueIcon(severity, true);
    assertNotNull(icon);
    assertEquals(StudioIcons.Common.ERROR_INLINE_SELECTED, icon);
  }

  @Test
  public void testIssueIconWarningInline() {
    HighlightSeverity severity = HighlightSeverity.INFORMATION;
    Icon icon = IssueModel.getIssueIcon(severity, false);
    assertNotNull(icon);
    assertEquals(StudioIcons.Common.WARNING_INLINE, icon);
  }

  @Test
  public void testIssueIconWarningInlineSelected() {
    HighlightSeverity severity = HighlightSeverity.INFORMATION;
    Icon icon = IssueModel.getIssueIcon(severity, true);
    assertNotNull(icon);
    assertEquals(StudioIcons.Common.WARNING_INLINE_SELECTED, icon);
  }

  @Test
  public void limitMaxNumberOfIssues() {
    IssueModel limitedIssueModel =
      IssueModel.createForTesting(myProjectRule.getTestRootDisposable(), myProjectRule.getProject(), 5);
    assertFalse(limitedIssueModel.hasIssues());
    limitedIssueModel.addIssueProvider(new RenderIssueProvider(
      null,
      createRenderErrorModel(
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.WARNING))));
    assertEquals(3, limitedIssueModel.getIssueCount());
    assertEquals(2, limitedIssueModel.getErrorCount());
    assertEquals(1, limitedIssueModel.getWarningCount());
    limitedIssueModel.addIssueProvider(new RenderIssueProvider(
      null,
      createRenderErrorModel(
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.WARNING))));

    assertEquals(6, limitedIssueModel.getIssueCount());
    assertEquals(4, limitedIssueModel.getErrorCount());
    assertEquals(1, limitedIssueModel.getWarningCount());
    Issue issue = Iterables.getLast(limitedIssueModel.getIssues());
    assertEquals("Too many issues found. 3 not shown.", issue.getSummary());
    assertEquals("Too many issues were found in this preview, not all of them will be shown in this panel.\n" +
                 "3 were found and not displayed.", issue.getDescription());
    assertEquals(HighlightSeverity.WEAK_WARNING, issue.getSeverity());
  }

  @Test
  public void testModelDescriptions() {
    ImmutableList.Builder<Issue> issueListBuilder = ImmutableList.builder();
    LintAnnotationsModel lintAnnotationsModel = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR);
    LintIssueProvider provider = new LintIssueProvider(lintAnnotationsModel);
    provider.collectIssues(issueListBuilder);
    ImmutableList<Issue> issues = issueListBuilder.build();
    assertEquals(1, issues.size());
    assertEquals("<BR/><BR/>titi<BR/><BR/><I>Issue id: toto</I><BR/><BR/>Vendor: Android Studio<br/>\n" +
                 "Identifier: MockIssueFactory<br/>\n" +
                 "Contact: studio@example.com<br/>\n" +
                 "Feedback: <a href=\"b/\">b/</a><br/>\n" +
                 "<BR/>", issues.get(0).getDescription());
  }

  @Test
  public void addAndRemoveIssueProvider() {
    IssueProvider fakeProvider = new IssueProvider() {
      @Override
      public void collectIssues(@NotNull ImmutableCollection.Builder<Issue> issueListBuilder) {
      }
    };
    assertTrue(fakeProvider.getListeners().isEmpty());

    myIssueModel.addIssueProvider(fakeProvider);
    assertEquals(1, fakeProvider.getListeners().size());
    assertEquals(myIssueModel.myUpdateCallback, fakeProvider.getListeners().get(0));

    myIssueModel.removeIssueProvider(fakeProvider);
    assertTrue(fakeProvider.getListeners().isEmpty());
  }

  public boolean hasRenderError() {
    return myIssueModel.getIssues()
      .stream()
      .anyMatch(issue -> issue instanceof RenderIssueProvider.NlRenderIssueWrapper && issue.getSeverity() == HighlightSeverity.ERROR);
  }
}