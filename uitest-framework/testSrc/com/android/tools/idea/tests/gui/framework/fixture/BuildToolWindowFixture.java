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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.content.Content;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class BuildToolWindowFixture extends ToolWindowFixture {
  BuildToolWindowFixture(@NotNull Project project, @NotNull Robot robot) {
    super(ToolWindowId.BUILD, project, robot);
  }

  /**
   * @return the console view in Sync tab of Build tool window.
   */
  @NotNull
  public ConsoleViewImpl getGradleSyncConsoleView() {
    Content syncContent = getContent("Sync");
    return myRobot.finder().findByType(syncContent.getComponent(), ConsoleViewImpl.class, true /* showing */);
  }

  /**
   * This method finds the given hyperlink text from console view, and performs mouse click on the text.
   * Please note that, because content in console view are regular textual strings, this method doesn't guarantee that
   * the given text is 'clickable'.
   *
   * @param consoleView   the component that contains hyperlink text.
   * @param hyperlinkText the text to search for and click on.
   * @throws AssertionError if hyperlink text is not found from console view.
   */
  public void findHyperlinkByTextAndClick(@NotNull ConsoleViewImpl consoleView, @NotNull String hyperlinkText) {
    String content = consoleView.getText();
    assert content.contains(hyperlinkText) : "Unable to find text: '" + hyperlinkText + "', Actual message: '" + content + "'";

    // Find click point in the middle of text area.
    int offsetOfHyperlink = content.indexOf(hyperlinkText) + hyperlinkText.length() / 2;
    Point clickPoint = GuiQuery.getNonNull(() -> consoleView.getEditor().offsetToXY(offsetOfHyperlink));

    // Click the hyperlink.
    myRobot.click(consoleView, clickPoint);
  }
}