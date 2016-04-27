/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.rendering.RenderErrorPanel;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

/**
 * Fixture representing render errors in a layout editor or the layout preview window
 */
public class RenderErrorPanelFixture {
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final Robot myRobot;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final LayoutFixture myLayoutFixture;
  private final RenderContext myRenderContext;

  public RenderErrorPanelFixture(@NotNull Robot robot, @NotNull LayoutFixture layoutFixture, @NotNull RenderContext renderContext) {
    myLayoutFixture = layoutFixture;
    myRenderContext = renderContext;
    myRobot = robot;
  }

  public void requireHaveRenderError(@NotNull String error) {
    RenderResult lastResult = myRenderContext.getLastResult();
    assertNotNull("No render result available", lastResult);
    RenderErrorPanel panel = new RenderErrorPanel();
    String html = panel.showErrors(lastResult);
    assertThat(html).contains(error);
  }

  public void performSuggestion(@NotNull String linkText) {
    RenderResult lastResult = myRenderContext.getLastResult();
    assertNotNull("No render result available", lastResult);
    RenderErrorPanel panel = new RenderErrorPanel();
    String html = panel.showErrors(lastResult);
    assertNotNull(html);
    // Find the URL for the corresponding linkText
    int index = html.indexOf(linkText);
    int anchor = html.lastIndexOf("<A HREF=\"", index);
    assertTrue("Could not find anchor before link text " + linkText + " in " + html, anchor != -1);
    int begin = anchor + "<A HREF=\"".length();
    int end = html.indexOf('"', begin);
    assertThat(end).isNotEqualTo(-1);
    String url = html.substring(begin, end);
    panel.performClick(url);
  }

  /** Asserts that the render was successful, optionally with some errors or no errors at all.  */
  public void requireRenderSuccessful(boolean allowErrors, boolean allowWarnings) {
    RenderResult lastResult = myRenderContext.getLastResult();
    assertNotNull("No render result available", lastResult);
    RenderLogger logger = lastResult.getLogger();

    if (logger.hasProblems()) {
      RenderErrorPanel panel = new RenderErrorPanel();
      String html = panel.showErrors(lastResult);
      if (allowWarnings) {
        assertTrue(html, allowErrors); // allowWarnings implies allowErrors
        assertFalse(html, logger.hasProblems());
      } else {
        assertFalse(html, logger.hasErrors());
      }
    }
  }

  public boolean haveErrors(boolean includeWarnings) {
    RenderResult lastResult = myRenderContext.getLastResult();
    assertNotNull("No render result available", lastResult);
    RenderLogger logger = lastResult.getLogger();

    return includeWarnings ? logger.hasProblems() : logger.hasErrors();
  }

  @NotNull
  public String getErrorHtml() {
    RenderResult lastResult = myRenderContext.getLastResult();
    assertNotNull("No render result available", lastResult);
    RenderLogger logger = lastResult.getLogger();

    if (logger.hasProblems()) {
      RenderErrorPanel panel = new RenderErrorPanel();
      String html = panel.showErrors(lastResult);
      if (html != null) {
        return html;
      }
    }

    return "";
  }
}
