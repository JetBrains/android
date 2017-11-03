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
package com.android.tools.idea.lint;

import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.detector.api.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class LintInspectionDescriptionLinkHandlerTest extends AndroidTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false);
      AndroidLintInspectionBase.resetDynamicTools();
    } finally {
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
                              "<br><br>More info:<br><a href=\"" +
                              "http://developer.android.com/tools/publishing/preparing.html#publishing-configure" +
                              "\">http://developer.android.com/tools/publishing/preparing.html#publishing-configure</a><br>";
    assertThat(handler.getDescription("MissingApplicationIcon", editor)).isEqualTo(issueExplanation);
  }

  public void testThirdPartyIssue() {
    Project project = getProject();
    Editor editor = mock(Editor.class);
    LintInspectionDescriptionLinkHandler handler = new LintInspectionDescriptionLinkHandler();

    String explanation = "The full explanation for the third party issue.";
    Issue testIssue = Issue.create(
      "TestThirdPartyIssue", "My Issue Summary",
      explanation,
      Category.CORRECTNESS, 6, Severity.WARNING, new Implementation(ApiDetector.class, Scope.JAVA_FILE_SCOPE));
    AndroidLintInspectionBase.getInspectionShortNameByIssue(project, testIssue);

    assertThat(handler.getDescription("TestThirdPartyIssue", editor)).isEqualTo(explanation);
  }
}