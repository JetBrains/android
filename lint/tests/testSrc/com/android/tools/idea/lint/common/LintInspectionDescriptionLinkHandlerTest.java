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
import com.intellij.codeInsight.hint.TooltipLinkHandlerEP;
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
  private JavaCodeInsightTestFixture myFixture;

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

    assertThat(TooltipLinkHandlerEP.getDescription("_unknown_", editor)).isNull();

    String issueExplanation = "You should set an icon for the application as whole because there is no default. " +
                              "This attribute must be set as a reference to a drawable resource containing the image " +
                              "(for example <code>@drawable/icon</code>)." +
                              "<br><br>Issue id: MissingApplicationIcon<br><br>More info:<br><a href=\"" +
                              "#lint/link_info<MissingApplicationIcon<" +
                              "https://developer.android.com/studio/publish/preparing#publishing-configure" +
                              "\">https://developer.android.com/studio/publish/preparing#publishing-configure</a><br><br>" +
                              "Vendor: Android Open Source Project<br/>" +
                              "Contact: <a href=\"" +
                              "#lint/link_info<MissingApplicationIcon<" +
                              "https://groups.google.com/g/lint-dev\">https://groups.google.com/g/lint-dev</a><br/>" +
                              "Feedback: <a href=\"" +
                              "#lint/link_info<MissingApplicationIcon<" +
                              "https://issuetracker.google.com/issues/new?component=192708\">" +
                              "https://issuetracker.google.com/issues/new?component=192708</a><br/>";
    String description = TooltipLinkHandlerEP.getDescription(LintInspectionDescriptionLinkHandler.LINK_PREFIX + "MissingApplicationIcon", editor);
    assertThat(description).isEqualTo(issueExplanation);
  }

  public void testNewlines() {
    Editor editor = mock(Editor.class);

    assertThat(TooltipLinkHandlerEP.getDescription("_unknown_", editor)).isNull();

    String issueExplanation = "<code>&lt;intent-filter></code> <code>&lt;data></code> tags should only declare a single unique " +
                              "attribute (i.e. scheme OR host, but not both). This better matches the runtime behavior of " +
                              "intent filters, as they combine all of the declared data attributes into a single matcher which " +
                              "is allowed to handle any combination across attribute types.<br/>" +
                              "<br/>" +
                              "For example, the following two <code>&lt;intent-filter></code> declarations are the same:<pre>\n" +
                              "&lt;intent-filter>\n" +
                              "    &lt;data android:scheme=\"http\" android:host=\"example.com\" />\n" +
                              "    &lt;data android:scheme=\"https\" android:host=\"example.org\" />\n" +
                              "&lt;/intent-filter>\n" +
                              "</pre><pre>\n" +
                              "&lt;intent-filter>\n" +
                              "    &lt;data android:scheme=\"http\"/>\n" +
                              "    &lt;data android:scheme=\"https\"/>\n" +
                              "    &lt;data android:host=\"example.com\" />\n" +
                              "    &lt;data android:host=\"example.org\" />\n" +
                              "&lt;/intent-filter>\n" +
                              "</pre><br/>" +
                              "They both handle all of the following:<br/>" +
                              "* <a href=\"#lint/link_info<IntentFilterUniqueDataAttributes<http://example.com\">http://example.com</a><br/>" +
                              "* <a href=\"#lint/link_info<IntentFilterUniqueDataAttributes<https://example.com\">https://example.com</a><br/>" +
                              "* <a href=\"#lint/link_info<IntentFilterUniqueDataAttributes<http://example.org\">http://example.org</a><br/>" +
                              "* <a href=\"#lint/link_info<IntentFilterUniqueDataAttributes<https://example.org\">https://example.org</a><br/>" +
                              "<br/>" +
                              "The second one better communicates the combining behavior and is clearer to an external reader that " +
                              "one should not rely on the scheme/host being self contained. It is not obvious in the first that " +
                              "<a href=\"#lint/link_info<IntentFilterUniqueDataAttributes<http://example.org\">http://example.org</a> " +
                              "is also matched, which can lead to confusion (or incorrect behavior) with a more complex set of " +
                              "schemes/hosts.<br/>" +
                              "<br/>" +
                              "Note that this does not apply to host + port, as those must be declared in the same " +
                              "<code>&lt;data></code> tag and are only associated with each other.<br>" +
                              "<br>" +
                              "Issue id: IntentFilterUniqueDataAttributes<br>" +
                              "<br>" +
                              "More info:<br>" +
                              "<a href=\"#lint/link_info<IntentFilterUniqueDataAttributes<https://developer.android.com/guide/components/intents-filters\">https://developer.android.com/guide/components/intents-filters</a><br>" +
                              "<br>" +
                              "Vendor: Android Open Source Project<br/>" +
                              "Contact: <a href=\"#lint/link_info<IntentFilterUniqueDataAttributes<https://groups.google.com/g/lint-dev\">https://groups.google.com/g/lint-dev</a><br/>" +
                              "Feedback: <a href=\"#lint/link_info<IntentFilterUniqueDataAttributes<https://issuetracker.google.com/issues/new?component=192708\">https://issuetracker.google.com/issues/new?component=192708</a><br/>";
    String description = TooltipLinkHandlerEP.getDescription(LintInspectionDescriptionLinkHandler.LINK_PREFIX + "IntentFilterUniqueDataAttributes", editor);
    assertThat(description).isEqualTo(issueExplanation);
  }

  public void testOption() {
    Editor editor = mock(Editor.class);

    assertThat(TooltipLinkHandlerEP.getDescription("_unknown_", editor)).isNull();

    String issueExplanation = "To improve referencing this code from Kotlin, consider adding explicit nullness information here with " +
                              "either <code>@NonNull</code> or <code>@Nullable</code>.<br><br>Issue id: UnknownNullness<br><br>" +
                              "Available options:<br/><br/>" +
                              "<b>ignore-deprecated</b> (default is false):<br/>Whether to ignore classes and members that have been " +
                              "annotated with <code>@Deprecated</code>.<br/><br/>Normally this lint check will flag all unannotated " +
                              "elements, but by setting this option to <code>true</code> it will skip any deprecated elements.<br/><br/>" +
                              "To configure this option, use a <code>lint.xml</code> file with an &lt;option> like this:<br/>" +
                              "<pre>\n" +
                              "&lt;lint>\n" +
                              "    &lt;issue id=\"UnknownNullness\">\n" +
                              "        &lt;option name=\"ignore-deprecated\" value=\"false\" />\n" +
                              "    &lt;/issue>\n" +
                              "&lt;/lint>\n" +
                              "</pre><br><br>" +
                              "More info:<br><a href=\"" +
                              "#lint/link_info<UnknownNullness<" +
                              "https://developer.android.com/kotlin/interop#nullability_annotations\">" +
                              "https://developer.android.com/kotlin/interop#nullability_annotations</a><br><br>" +
                              "Vendor: Android Open Source Project<br/>" +
                              "Contact: <a href=\"" +
                              "#lint/link_info<UnknownNullness<" +
                              "https://groups.google.com/g/lint-dev\">https://groups.google.com/g/lint-dev</a><br/>" +
                              "Feedback: <a href=\"" +
                              "#lint/link_info<UnknownNullness<" +
                              "https://issuetracker.google.com/issues/new?component=192708\">" +
                              "https://issuetracker.google.com/issues/new?component=192708</a><br/>";
    String description = TooltipLinkHandlerEP.getDescription(LintInspectionDescriptionLinkHandler.LINK_PREFIX + "UnknownNullness", editor);
    assertThat(description).isEqualTo(issueExplanation);
  }

  public void testThirdPartyIssue() {
    Project project = myFixture.getProject();
    Editor editor = mock(Editor.class);
    Mockito.when(editor.getProject()).thenReturn(project);

    String explanation = "The full explanation for the third party issue.";
    Issue testIssue = Issue.create(
      "TestThirdPartyIssue", "My Issue Summary",
      explanation,
      Category.CORRECTNESS, 6, Severity.WARNING, new Implementation(ApiDetector.class, Scope.JAVA_FILE_SCOPE));
    AndroidLintInspectionBase.getInspectionShortNameByIssue(project, testIssue);

    String description = TooltipLinkHandlerEP.getDescription(LintInspectionDescriptionLinkHandler.LINK_PREFIX + "TestThirdPartyIssue", editor);
    assertThat(description).isEqualTo(explanation + "<br><br>Issue id: TestThirdPartyIssue");
  }
}
