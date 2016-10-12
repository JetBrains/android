/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.dom;

public class AndroidLintDomTest extends AndroidDomTestCase {
  public AndroidLintDomTest() {
    super("dom/lint");
  }

  public void testIssueAttributeCompletion() throws Throwable {
    doTestCompletionVariants("issue_tag_attribute.xml", "id", "severity");
  }

  public void testSeverityValuesCompletion() throws Throwable {
    doTestCompletionVariants("issue_severity.xml", "fatal", "informational", "ignore", "error", "warning");
  }

  public void testIssueIdCompletion() throws Throwable {
    doTestCompletionVariants("issue_id_completion.xml", "UselessLeaf", "UselessParent");
  }

  // Attributes other than "id" and "severity" are not allowed and should be highlighted in red
  public void testExtraAttributes() throws Throwable {
    doTestHighlighting("issue_extra_attributes.xml");
  }

  public void testIgnoreTagCompletion() throws Throwable {
    toTestCompletion("issue_ignore_completion.xml", "issue_ignore_completion_after.xml");
  }

  public void testIgnorePathAttributeCompletion() throws Throwable {
    toTestCompletion("issue_ignore_path_completion.xml", "issue_ignore_path_completion_after.xml");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return testFileName;
  }
}
