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

import org.junit.Before;
import org.junit.Test;

import static com.android.tools.idea.gradle.structure.model.PsIssue.Type.WARNING;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link PsIssues}.
 */
public class PsIssuesTest {
  private PsIssues myIssues;

  @Before
  public void setUp() {
    myIssues = new PsIssues();
  }

  @Test
  public void testGetTooltipText() {
    for (int i = 0; i < 5; i++) {
      myIssues.addIssue(new PsIssue("Issue " + (i + 1), WARNING));
    }
    assertEquals("<html><body>Issue 1<br>Issue 2<br>Issue 3<br>Issue 4<br>Issue 5<br></body></html>", myIssues.getTooltipText());

    for (int i = 5; i < 20; i++) {
      myIssues.addIssue(new PsIssue("Issue " + (i + 1), WARNING));
    }

    assertEquals("<html><body>Issue 1<br>Issue 2<br>Issue 3<br>Issue 4<br>Issue 5<br>Issue 6<br>Issue 7<br>Issue 8<br>Issue 9<br>" +
                 "Issue 10<br>Issue 11<br>9 more problems...<br></body></html>", myIssues.getTooltipText());
  }
}