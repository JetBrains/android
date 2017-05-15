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
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Comparator;
import java.util.List;

import static com.android.tools.idea.gradle.structure.model.PsIssue.Severity.ERROR;
import static com.android.tools.idea.gradle.structure.model.PsIssue.Severity.WARNING;
import static com.android.tools.idea.gradle.structure.model.PsIssueType.LIBRARY_UPDATES_AVAILABLE;
import static com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS;
import static com.android.tools.idea.gradle.structure.model.PsPath.EMPTY_PATH;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link PsIssueCollection}.
 */
public class PsIssueCollectionTest {
  private PsIssueCollection myIssueCollection;
  private PsPath myTestPath;
  @Mock private PsContext myContext;

  @Before
  public void setUp() {
    initMocks(this);
    myIssueCollection = new PsIssueCollection(myContext);
    myTestPath = new TestPath("test path");
  }

  @Test
  public void getTooltipText_empty() {
    assertNull(PsIssueCollection.getTooltipText(ImmutableList.of(), true));
  }

  @Test
  public void getTooltipText_singleIssueWithPath() {
    myIssueCollection.add(new PsIssue("Issue 01", myTestPath, PROJECT_ANALYSIS, WARNING));
    List<PsIssue> issues = myIssueCollection.getValues();
    String expected = "<html><body>" +
                      "test path: Issue 01<br>" +
                      "</body></html>";
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, true));
  }

  @Test
  public void getTooltipText_singleIssueWithoutPath() {
    myIssueCollection.add(new PsIssue("Issue 01", myTestPath, PROJECT_ANALYSIS, WARNING));
    List<PsIssue> issues = myIssueCollection.getValues();
    String expected = "<html><body>" +
                      "Issue 01<br>" +
                      "</body></html>";
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, false));
  }

  @Test
  public void getTooltipText_multipleIssuesWithPath() {
    for (int i = 1; i < 4; i++) {
      myIssueCollection.add(new PsIssue(String.format("Empty Issue %02d", i), EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
      myIssueCollection.add(new PsIssue(String.format("Test Issue %02d", i), myTestPath, PROJECT_ANALYSIS, WARNING));
    }
    List<PsIssue> issues = myIssueCollection.getValues();
    String expected = "<html><body><ul>" +
                      "<li>Empty Issue 01</li>" +
                      "<li>Empty Issue 02</li>" +
                      "<li>Empty Issue 03</li>" +
                      "<li>test path: Test Issue 01</li>" +
                      "<li>test path: Test Issue 02</li>" +
                      "<li>test path: Test Issue 03</li>" +
                      "</ul></body></html>";
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, true));
  }

  @Test
  public void getTooltipText_multipleIssuesWithoutPath() {
    for (int i = 1; i < 4; i++) {
      myIssueCollection.add(new PsIssue(String.format("Empty Issue %02d", i), EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
      myIssueCollection.add(new PsIssue(String.format("Test Issue %02d", i), myTestPath, PROJECT_ANALYSIS, WARNING));
    }
    List<PsIssue> issues = myIssueCollection.getValues();
    String expected = "<html><body><ul>" +
                      "<li>Empty Issue 01</li>" +
                      "<li>Empty Issue 02</li>" +
                      "<li>Empty Issue 03</li>" +
                      "<li>Test Issue 01</li>" +
                      "<li>Test Issue 02</li>" +
                      "<li>Test Issue 03</li>" +
                      "</ul></body></html>";
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, false));
  }

  @Test
  public void getTooltipText_manyIssuesWithPath() {
    for (int i = 1; i < 9; i++) {
      myIssueCollection.add(new PsIssue(String.format("Empty Issue %02d", i), EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
      myIssueCollection.add(new PsIssue(String.format("Test Issue %02d", i), myTestPath, PROJECT_ANALYSIS, WARNING));
    }
    List<PsIssue> issues = myIssueCollection.getValues();
    String expected = "<html><body><ul>" +
                      "<li>Empty Issue 01</li>" +
                      "<li>Empty Issue 02</li>" +
                      "<li>Empty Issue 03</li>" +
                      "<li>Empty Issue 04</li>" +
                      "<li>Empty Issue 05</li>" +
                      "<li>Empty Issue 06</li>" +
                      "<li>Empty Issue 07</li>" +
                      "<li>Empty Issue 08</li>" +
                      "<li>test path: Test Issue 01</li>" +
                      "<li>test path: Test Issue 02</li>" +
                      "<li>test path: Test Issue 03</li>" +
                      "</ul>5 more messages...<br></body></html>";
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, true));
  }

  @Test
  public void getTooltipText_manyIssuesWithoutPath() {
    for (int i = 1; i < 9; i++) {
      myIssueCollection.add(new PsIssue(String.format("Empty Issue %02d", i), EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
      myIssueCollection.add(new PsIssue(String.format("Test Issue %02d", i), myTestPath, PROJECT_ANALYSIS, WARNING));
    }
    List<PsIssue> issues = myIssueCollection.getValues();
    String expected = "<html><body><ul>" +
                      "<li>Empty Issue 01</li>" +
                      "<li>Empty Issue 02</li>" +
                      "<li>Empty Issue 03</li>" +
                      "<li>Empty Issue 04</li>" +
                      "<li>Empty Issue 05</li>" +
                      "<li>Empty Issue 06</li>" +
                      "<li>Empty Issue 07</li>" +
                      "<li>Empty Issue 08</li>" +
                      "<li>Test Issue 01</li>" +
                      "<li>Test Issue 02</li>" +
                      "<li>Test Issue 03</li>" +
                      "</ul>5 more messages...<br></body></html>";
    assertEquals(expected, PsIssueCollection.getTooltipText(issues, false));
  }

  @Test
  public void findIssues_withModuleModel() {
    PsModule mockModule = mock(PsModule.class);
    PsIssue issueA = new PsIssue("a", new PsModulePath(mockModule), PROJECT_ANALYSIS, WARNING);
    myIssueCollection.add(issueA);
    myIssueCollection.add(new PsIssue("b", EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
    List<PsIssue> issues = myIssueCollection.findIssues(mockModule, null);
    assertThat(issues).containsExactly(issueA);
  }

  @Test
  public void findIssues_withUnknownType() {
    myIssueCollection.add(new PsIssue("a", EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
    myIssueCollection.add(new PsIssue("b", EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
    List<PsIssue> issues = myIssueCollection.findIssues(new PsModel(null) {
      @NotNull
      @Override
      public String getName() {
        return "test";
      }

      @Override
      public boolean isDeclared() {
        return false;
      }

      @Nullable
      @Override
      public Object getResolvedModel() {
        return null;
      }
    }, Comparator.comparing(PsIssue::getText));
    assertThat(issues).isEmpty();
  }

  @Test
  public void findIssues_withComparator() {
    PsIssue issueA = new PsIssue("a", EMPTY_PATH, PROJECT_ANALYSIS, WARNING);
    PsIssue issueB = new PsIssue("b", EMPTY_PATH, PROJECT_ANALYSIS, WARNING);
    PsIssue issueC = new PsIssue("c", EMPTY_PATH, PROJECT_ANALYSIS, WARNING);
    PsIssue issueD = new PsIssue("d", EMPTY_PATH, PROJECT_ANALYSIS, WARNING);

    myIssueCollection.add(issueB);
    myIssueCollection.add(issueD);
    myIssueCollection.add(issueC);
    myIssueCollection.add(issueA);
    List<PsIssue> issues = myIssueCollection.findIssues(EMPTY_PATH, Comparator.comparing(PsIssue::getText));
    assertThat(issues).containsExactly(issueA, issueB, issueC, issueD).inOrder();
  }

  @Test
  public void findIssues_nomatch() {
    myIssueCollection.add(new PsIssue("", EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
    assertThat(myIssueCollection.findIssues(myTestPath, null)).isEmpty();
  }

  @Test
  public void add() {
    PsIssue testIssue = new PsIssue("", EMPTY_PATH, PROJECT_ANALYSIS, WARNING);
    myIssueCollection.add(testIssue);
    assertThat(myIssueCollection.getValues()).containsExactly(testIssue);
  }

  @Test
  public void add_withExtraPath() {
    PsIssue testIssue = new PsIssue("", EMPTY_PATH, PROJECT_ANALYSIS, WARNING);
    testIssue.setExtraPath(myTestPath);
    myIssueCollection.add(testIssue);
    assertThat(myIssueCollection.getValues()).containsExactly(testIssue, testIssue);
  }

  @Test
  public void remove() {
    PsIssue issueA = new PsIssue("a", EMPTY_PATH, PROJECT_ANALYSIS, WARNING);
    PsIssue issueB = new PsIssue("b", EMPTY_PATH, LIBRARY_UPDATES_AVAILABLE, WARNING);
    myIssueCollection.add(issueA);
    myIssueCollection.add(issueB);
    assertThat(myIssueCollection.getValues()).containsExactly(issueA, issueB);
    myIssueCollection.remove(PROJECT_ANALYSIS);
    assertThat(myIssueCollection.getValues()).containsExactly(issueB);
  }

  @Test
  public void isEmpty() {
    assertThat(myIssueCollection.getValues()).isEmpty();
    assertTrue(myIssueCollection.isEmpty());
    myIssueCollection.add(new PsIssue("", EMPTY_PATH, PROJECT_ANALYSIS, WARNING));
    assertThat(myIssueCollection.getValues()).isNotEmpty();
    assertFalse(myIssueCollection.isEmpty());
  }

  @Test
  public void getValues() {
    PsIssue issueA = new PsIssue("a", EMPTY_PATH, PROJECT_ANALYSIS, WARNING);
    PsIssue issueB = new PsIssue("b", myTestPath, LIBRARY_UPDATES_AVAILABLE, WARNING);
    myIssueCollection.add(issueA);
    myIssueCollection.add(issueB);
    assertThat(myIssueCollection.getValues()).containsExactly(issueA, issueB);
  }

  @Test
  public void getValues_byType() {
    PsIssue issueA = new PsIssue("a", EMPTY_PATH, PROJECT_ANALYSIS, WARNING);
    PsIssue issueB = new PsIssue("b", myTestPath, LIBRARY_UPDATES_AVAILABLE, ERROR);
    PsIssue issueC = new PsIssue("c", myTestPath, LIBRARY_UPDATES_AVAILABLE, ERROR);
    myIssueCollection.add(issueA);
    myIssueCollection.add(issueB);
    myIssueCollection.add(issueC);
    assertThat(myIssueCollection.getValues(EMPTY_PATH.getClass())).containsExactly(issueA);
    assertThat(myIssueCollection.getValues(myTestPath.getClass())).containsExactly(issueB, issueC);
  }
}