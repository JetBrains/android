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
package com.android.tools.idea.gradle.structure.model;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import static com.android.tools.idea.gradle.structure.model.PsIssue.Severity.WARNING;
import static com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS;
import static org.junit.Assert.assertEquals;

public class PsIssueTest {
  // using "test" prefix to avoid overriding toString for this class
  @Test
  public void testToString() {
    PsIssue issue = new PsIssue("text", "description", new TestPath("testPath"), PROJECT_ANALYSIS, WARNING);
    assertEquals("WARNING: text", issue.toString());
  }

  @Test
  public void equalsAndHashCode() {
    EqualsVerifier.forClass(PsIssue.class)
      .withIgnoredFields("myExtraPath", "myQuickFixPath")
      .verify();
  }
}
