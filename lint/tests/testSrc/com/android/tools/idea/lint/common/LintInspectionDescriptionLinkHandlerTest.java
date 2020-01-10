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
package com.android.tools.idea.lint.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.mockito.Mockito;

public class LintInspectionDescriptionLinkHandlerTest extends UsefulTestCase {
  protected JavaCodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
      AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testBuiltinIssue() {
    Editor editor = mock(Editor.class);
    LintInspectionDescriptionLinkHandler handler = new LintInspectionDescriptionLinkHandler();

    assertThat(handler.getDescription("_unknown_", editor)).isNull();

    String issueExplanation = "You should set an icon for the application as whole because there is no default. " +
                              "This attribute must be set as a reference to a drawable resource containing the image " +
                              "(for example <code>@drawable/icon</code>)." +
                              "<br><br>Issue id: MissingApplicationIcon<br><br>More info:<br><a href=\"" +
                              "https://developer.android.com/studio/publish/preparing#publishing-configure" +
                              "\">https://developer.android.com/studio/publish/preparing#publishing-configure</a><br>";
    assertThat(handler.getDescription("MissingApplicationIcon", editor)).isEqualTo(issueExplanation);
  }

  public void testThirdPartyIssue() {
    Project project = myFixture.getProject();
    Editor editor = mock(Editor.class);
    Mockito.when(editor.getProject()).thenReturn(project);
    LintInspectionDescriptionLinkHandler handler = new LintInspectionDescriptionLinkHandler();

    String explanation = "The full explanation for the third party issue.";
    Issue testIssue = Issue.create(
      "TestThirdPartyIssue", "My Issue Summary",
      explanation,
      Category.CORRECTNESS, 6, Severity.WARNING, new Implementation(ApiDetector.class, Scope.JAVA_FILE_SCOPE));
    AndroidLintInspectionBase.getInspectionShortNameByIssue(project, testIssue);

    assertThat(handler.getDescription("TestThirdPartyIssue", editor)).isEqualTo(explanation + "<br><br>Issue id: TestThirdPartyIssue");
  }
}
