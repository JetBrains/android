/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.android.tools.idea.gradle.structure.model.PsIssue.Severity.WARNING;
import static com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS;
import static com.android.tools.idea.gradle.structure.navigation.PsNavigationPath.EMPTY_PATH;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link PsIssueCollection}.
 */
public class PsIssueCollectionTest {
  private PsIssueCollection myIssueCollection;

  @Before
  public void setUp() {
    myIssueCollection = new PsIssueCollection(Mockito.mock(PsContext.class));
  }

  @Test
  public void testGetTooltipText() {
    for (int i = 0; i < 5; i++) {
      myIssueCollection.add(new PsIssue("Issue " + (i + 1), EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
    }
    List<PsIssue> issues = myIssueCollection.getValues();
    String expected = "<html><body>Issue 1<br>Issue 2<br>Issue 3<br>Issue 4<br>Issue 5<br></body></html>";
    assertEquals(expected, PsIssueCollection.getTooltipText(issues));

    myIssueCollection.clear();

    for (int i = 0; i < 16; i++) {
      int issueNumber = i + 1;
      String text = "Issue " + String.format("%02d", issueNumber);
      myIssueCollection.add(new PsIssue(text, EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
    }
    issues = myIssueCollection.getValues();
    expected = "<html><body>Issue 01<br>Issue 02<br>Issue 03<br>Issue 04<br>Issue 05<br>Issue 06<br>Issue 07<br>Issue 08<br>Issue 09<br>" +
               "Issue 10<br>Issue 11<br>5 more problems...<br></body></html>";
    assertEquals(expected, PsIssueCollection.getTooltipText(issues));
  }
}