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
package org.jetbrains.android.inspections.lint;

import com.android.tools.idea.lint.AndroidLintHardcodedTextInspection;
import com.android.tools.idea.lint.AndroidLintIdeIssueRegistry;
import com.android.tools.idea.lint.AndroidLintTypographyDashesInspection;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.Vendor;
import com.android.tools.lint.detector.api.Issue;
import com.google.common.base.Joiner;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

public class AndroidLintInspectionBaseTest extends TestCase {
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
    IssueRegistry registry = new IssueRegistry() {
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
}