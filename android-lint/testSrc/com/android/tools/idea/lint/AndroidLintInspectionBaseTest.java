/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.lint.common.AndroidLintIgnoreWithoutReasonInspection;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidLintUnknownNullnessInspection;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.inspections.AndroidLintHardcodedTextInspection;
import com.android.tools.idea.lint.inspections.AndroidLintObjectAnimatorBindingInspection;
import com.android.tools.idea.lint.inspections.AndroidLintSdCardPathInspection;
import com.android.tools.idea.lint.inspections.AndroidLintTypographyDashesInspection;
import com.android.tools.idea.lint.inspections.AndroidLintUnusedResourcesInspection;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.Vendor;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.google.common.base.Joiner;
import com.intellij.codeInsight.intention.PriorityAction.Priority;
import com.intellij.psi.PsiFile;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase;
import org.jetbrains.annotations.NotNull;

public class AndroidLintInspectionBaseTest extends LightJavaCodeInsightFixtureAdtTestCase {
  public void testGroup() {
    AndroidLintInspectionBase inspection;

    inspection = new AndroidLintHardcodedTextInspection();
    assertEquals("Android Lint: Internationalization", inspection.getGroupDisplayName());
    assertEquals("Android\nLint\nInternationalization", Joiner.on("\n").join(inspection.getGroupPath()));

    inspection = new AndroidLintTypographyDashesInspection();
    assertEquals("Android Lint: Usability", inspection.getGroupDisplayName());
    assertEquals("Android\nLint\nUsability\nTypography", Joiner.on("\n").join(inspection.getGroupPath()));
  }

  public void testVendor() {
    Issue issue = new AndroidLintIdeIssueRegistry().getIssue("HardcodedText");
    assertNotNull(issue);

    // We normally don't include AOSP vendor info:
    AndroidLintHardcodedTextInspection inspection = new AndroidLintHardcodedTextInspection();
    assertSame(issue, inspection.getIssue());
    String desc = inspection.getStaticDescription();
    assertEquals(
      "" +
       "<html><body>Hardcoded text<br><br>Hardcoding text attributes directly in layout files is bad for several reasons:<br/>\n" +
      "<br/>\n" +
      "* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)<br/>\n" +
      "<br/>\n" +
      "* The application cannot be translated to other languages by just adding new translations for existing string resources.<br/>\n" +
      "<br/>\n" +
      "There are quickfixes to automatically extract this hardcoded string into a resource lookup.<br><br>Issue id: HardcodedText</body></html>",
      desc);

    // Verify what it would look like for a third party check
    @SuppressWarnings("LintImplUseKotlin") IssueRegistry registry = new IssueRegistry() {
      @NotNull
      @Override
      public List<Issue> getIssues() {
        return Collections.singletonList(issue);
      }

      @NotNull
      @Override
      public Vendor getVendor() {
        return new Vendor(
          "Example Vendor",
          "AndroidLintInspectionBaseTest"
        );
      }
    };
    IssueRegistry prevRegistry = issue.getRegistry();
    try {
      issue.setRegistry(registry);
      assertNull(issue.getVendor());
      desc = inspection.getStaticDescription();
      assertEquals(
        "" +
        "<html><body>Hardcoded text<br><br>Hardcoding text attributes directly in layout files is bad for several reasons:<br/>\n" +
        "<br/>\n" +
        "* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)<br/>\n" +
        "<br/>\n" +
        "* The application cannot be translated to other languages by just adding new translations for existing string resources.<br/>\n" +
        "<br/>\n" +
        "There are quickfixes to automatically extract this hardcoded string into a resource lookup.<br><br>Issue id: HardcodedText<br/><br/>\n" +
        "Vendor: Example Vendor<br/>\n" +
        "Identifier: AndroidLintInspectionBaseTest<br/>\n" +
        "</body></html>",
        desc);
    } finally {
      issue.setRegistry(prevRegistry);
    }
  }

  public void testOptions() {
    AndroidLintUnknownNullnessInspection inspection = new AndroidLintUnknownNullnessInspection();
    String desc = inspection.getStaticDescription();
    assertEquals(
      "" +
      "<html><body>Unknown nullness<br><br>To improve referencing this code from Kotlin, consider adding explicit nullness information here with either <code>@NonNull</code> or <code>@Nullable</code>.<br><br>Issue id: UnknownNullness<br><br>Available options:<br/>\n" +
      "<br/>\n" +
      "<b>ignore-deprecated</b> (default is false):<br/>\n" +
      "Whether to ignore classes and members that have been annotated with <code>@Deprecated</code>.<br/>\n" +
      "<br/>\n" +
      "Normally this lint check will flag all unannotated elements, but by setting this option to <code>true</code> it will skip any deprecated elements.<br/>\n" +
      "<br/>\n" +
      "To configure this option, use a <code>lint.xml</code> file with an &lt;option> like this:<br/>\n" +
      "\n" +
      "<pre>\n" +
      "&lt;lint>\n" +
      "    &lt;issue id=\"UnknownNullness\">\n" +
      "        &lt;option name=\"ignore-deprecated\" value=\"false\" />\n" +
      "    &lt;/issue>\n" +
      "&lt;/lint>\n" +
      "</pre>\n" +
      "<br><br>" +
      "<a href=\"https://developer.android.com/kotlin/interop#nullability_annotations\">https://developer.android.com/kotlin/interop#nullability_annotations</a></body></html>",
      desc);
  }

  public void testSortingPriority() {
    PsiFile file = myFixture.configureByText("UserDao.java", "package test.pkg;\nclass Test {\n}");
    LintFix first = LintFix.create().name("First Alphabetically").replace().text("Test").with("1").build();
    LintFix second = LintFix.create().name("Second Alphabetically").replace().text("Test").with("1").build();
    LintFix third = LintFix.create().name("Third Alphabetically").replace().text("Test").with("1").build();
    LintFix group1 = LintFix.create().alternatives(first, second, third);

    LintIdeQuickFix[] fixes = AndroidLintInspectionBase.createFixes(file, group1);
    assertEquals("First Alphabetically", fixes[0].getName());
    assertEquals(Priority.TOP, fixes[0].getPriority());
    assertEquals("Second Alphabetically", fixes[1].getName());
    assertEquals(Priority.HIGH, fixes[1].getPriority());
    assertEquals("Third Alphabetically", fixes[2].getName());
    // Ideally we'd use NORMAL here but all intention actions have to be HIGH or above, otherwise they'll
    // filter below other random IntelliJ generic actions (like Git rollback changes on current line etc)
    assertEquals(Priority.HIGH, fixes[2].getPriority());
  }

  public void testWorksInBatchMode() {
    // implementation scope requires multiple file, no analysis scopes
    assertTrue(new AndroidLintUnusedResourcesInspection().worksInBatchModeOnly());
    // implementation scope only specifies a single file type (java/kotlin)
    assertFalse(new AndroidLintSdCardPathInspection().worksInBatchModeOnly());
    // implementation scope specifies two scopes, but one of them is TEST_SOURCES
    assertFalse(new AndroidLintIgnoreWithoutReasonInspection().worksInBatchModeOnly());
    // implementation scope requires multiple files, but one of the analysis scopes is single file
    assertFalse(new AndroidLintObjectAnimatorBindingInspection().worksInBatchModeOnly());
  }
}