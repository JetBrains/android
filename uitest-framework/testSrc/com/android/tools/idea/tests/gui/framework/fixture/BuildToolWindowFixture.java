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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.openapi.wm.impl.content.ContentTabLabelFixture;
import com.intellij.ui.content.Content;
import java.awt.Point;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class BuildToolWindowFixture extends ToolWindowFixture {

  BuildToolWindowFixture(@NotNull Project project, @NotNull Robot robot) {
    super("Build", project, robot);
  }

  public Content getSyncContent() {
    return getContent("Sync");
  }

  /**
   * @return the console view in Sync tab of Build tool window.
   */
  @NotNull
  public ConsoleViewImpl getGradleSyncConsoleView() {
    Content syncContent = getSyncContent();
    return myRobot.finder().findByType(syncContent.getComponent(), ConsoleViewImpl.class, true /* showing */);
  }

  @NotNull
  public JTreeFixture getGradleSyncEventTree() {
    Content syncContent = getSyncContent();
    JTree tree = myRobot.finder().findByType(syncContent.getComponent(), JTree.class, true /* showing */);
    return new JTreeFixture(myRobot, tree);
  }

  public Content getBuildContent() {
    return getContent("Build Output");
  }

  @NotNull
  public ConsoleViewImpl getGradleBuildConsoleView() {
    Content buildContent = getBuildContent();
    return myRobot.finder().findByType(buildContent.getComponent(), ConsoleViewImpl.class, true /* showing */);
  }

  @NotNull
  public JTreeFixture getGradleBuildEventTree() {
    Content syncContent = getBuildContent();
    JTree tree = myRobot.finder().findByType(syncContent.getComponent(), JTree.class, true /* showing */);
    return new JTreeFixture(myRobot, tree);
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
    Point clickPoint = GuiQuery.getNonNull(() -> {
      Point point = consoleView.getEditor().offsetToXY(offsetOfHyperlink);
      point.translate(0, -consoleView.getEditor().getScrollingModel().getVerticalScrollOffset());
      return point;
    });
    // Click the hyperlink.
    myRobot.click(consoleView, clickPoint);
  }

  /**
   * Get the text of content of the sync console view
   *
   * @return
   */
  public String getSyncConsoleViewText() {
    Content syncContent = getContent("Sync");
    ConsoleViewImpl consoleView = myRobot.finder().findByType(syncContent.getComponent(), ConsoleViewImpl.class, true /* showing */);
    return consoleView.getText();
  }

  private InternalDecorator getToolWindowInternalDecorator() {
    return ((ToolWindowEx)myToolWindow).getDecorator();
  }

  public void waitTabExist(@NotNull String displayName) {
    ComponentMatcher matcher = Matchers.byText(BaseLabel.class, displayName);
    GuiTests.waitUntilShowing(myRobot, getToolWindowInternalDecorator(), new GenericTypeMatcher<BaseLabel>(BaseLabel.class) {
      @Override
      protected boolean isMatching(@NotNull BaseLabel component) {
        return matcher.matches(component);
      }
    }, 10);
  }

  public void waitTabNotExist(@NotNull String displayName) {
    ComponentMatcher matcher = Matchers.byText(BaseLabel.class, displayName);
    GuiTests.waitUntilGone(myRobot, getToolWindowInternalDecorator(), new GenericTypeMatcher<BaseLabel>(BaseLabel.class) {
      @Override
      protected boolean isMatching(@NotNull BaseLabel component) {
        return matcher.matches(component);
      }
    }, 10);
  }

  public BuildAnalyzerViewFixture waitBuildAnalyzerTabOpened() {
    JComponent contentComponent = getContent("Build Analyzer").getComponent();
    Wait.seconds(10).expecting("Build tab 'Build Analyzer' to be visible")
      .until(contentComponent::isVisible);
    return new BuildAnalyzerViewFixture(myRobot, (JPanel)contentComponent);
  }

  private void clickTab(@NotNull String name) {
    ContentTabLabelFixture buildTab = ContentTabLabelFixture.findByText(myRobot, getToolWindowInternalDecorator(), name, 3);
    buildTab.click();
  }

  private void clickCloseTab(@NotNull String name) {
    ContentTabLabelFixture buildTab =
      ContentTabLabelFixture.findByText(myRobot, getToolWindowInternalDecorator(), name, 3);
    buildTab.close();
  }

  public BuildAnalyzerViewFixture openBuildAnalyzerUsingTabHeaderClick() {
    clickTab("Build Analyzer");
    return waitBuildAnalyzerTabOpened();
  }

  public BuildAnalyzerViewFixture openBuildAnalyzerUsingBuildOutputLink() {
    findHyperlinkByTextAndClick(getGradleBuildConsoleView(), "Build Analyzer");
    waitTabExist("Build Analyzer");
    return waitBuildAnalyzerTabOpened();
  }

  public void closeBuildAnalyzerTab() {
    clickCloseTab("Build Analyzer");
    waitTabNotExist("Build Analyzer");
  }
}