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
package com.android.tools.idea.gradle.structure.configurables.issues;

import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsIssueType;
import com.android.tools.idea.gradle.structure.model.PsPath;
import org.hamcrest.CoreMatchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DependencyViewIssueRendererTest {
  private PsPath myTestIssuePath;
  private PsIssue myTestIssue;
  private PsPath myQuickFixPath;

  @Before
  public void setUp() {
    myTestIssuePath = createPath("/PATH");
    myQuickFixPath = createPath("/QUICK_FIX");
    myTestIssue = new PsIssue("TEXT", "DESCRIPTION", myTestIssuePath, PsIssueType.PROJECT_ANALYSIS, PsIssue.Severity.ERROR);
  }

  @NotNull
  private PsPath createPath(final String text) {
    return new PsPath() {
      @NotNull
      @Override
      public String toText(@NotNull TexType type) {
        switch (type) {
          case FOR_COMPARE_TO:
            return "FOR_COMPARE_" + text;
          case HTML:
            return "<" + text + ">";
          case PLAIN_TEXT:
            return "PLAIN_TEXT_" + text;
          default:
            throw new AssertionError();
        }
      }

      @Nullable
      @Override
      public String getHyperlinkDestination() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Test
  public void testRenderIssue() {
    IssueRenderer renderer = new DependencyViewIssueRenderer(false, false);
    assertThat(renderIssue(renderer), CoreMatchers.is("TEXT"));
  }

  @Test
  public void testRenderIssue_quickFix() {
    myTestIssue.setQuickFixPath(myQuickFixPath);
    IssueRenderer renderer = new DependencyViewIssueRenderer(false, false);
    assertThat(renderIssue(renderer), CoreMatchers.is("TEXT </QUICK_FIX>"));
  }

  @Test
  public void testRenderIssue_renderPath() {
    IssueRenderer renderer = new DependencyViewIssueRenderer(true, false);
    assertThat(renderIssue(renderer), CoreMatchers.is("</PATH>: TEXT"));
  }

  @Test
  public void testRenderIssue_renderPathAndQuickFix() {
    myTestIssue.setQuickFixPath(myQuickFixPath);
    IssueRenderer renderer = new DependencyViewIssueRenderer(true, false);
    assertThat(renderIssue(renderer), CoreMatchers.is("</PATH>: TEXT </QUICK_FIX>"));
  }

  @NotNull
  private String renderIssue(IssueRenderer renderer) {
    StringBuilder sb = new StringBuilder();
    renderer.renderIssue(sb, myTestIssue);
    return sb.toString();
  }

  @Test
  public void testRenderIssue_renderDescription() {
    IssueRenderer renderer = new DependencyViewIssueRenderer(false, true);
    assertThat(renderIssue(renderer), CoreMatchers.is("TEXT<br/><br/>DESCRIPTION"));
  }

  @Test
  public void testRenderIssue_renderDescriptionAndQuickFix() {
    myTestIssue.setQuickFixPath(myQuickFixPath);
    IssueRenderer renderer = new DependencyViewIssueRenderer(false, true);
    assertThat(renderIssue(renderer), CoreMatchers.is("TEXT </QUICK_FIX><br/><br/>DESCRIPTION"));
  }

}