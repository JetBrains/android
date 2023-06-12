/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Lists;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class AGPUpgradeAssistantDialogFixture implements ContainerFixture<JDialog> {
  @NotNull private final IdeFrameFixture myIdeFrameFixture;
  @NotNull private final JDialog myDialog;
  @NotNull static final String TITLE = "Android Gradle Plugin Upgrade Assistant";

  @NotNull
  public static AGPUpgradeAssistantDialogFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(),
                                               Matchers.byTitle(JDialog.class, TITLE));
    return new AGPUpgradeAssistantDialogFixture(ideFrameFixture, dialog);
  }

  private AGPUpgradeAssistantDialogFixture(@NotNull IdeFrameFixture ideFrameFixture,
                                  @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
  }


  public void clickBeginUpgrade() {
    String buttonName = "Begin Upgrade";
    JButton beginUpgradeButton = robot().finder().find(target(), Matchers.byText(JButton.class, buttonName));
    beginUpgradeButton.isEnabled();
    robot().click(beginUpgradeButton);
    waitForDialogToDisappear();
  }

  public boolean checkAllButtons() {
    List<JButton> allButtons = Lists.newArrayList(robot().finder().findAll(target(), Matchers.byType(JButton.class)));
    if (allButtons.size() != 3) {
      return false;
    }
    return true;
  }

  public boolean checkIfProjectNameMentionedInDialog(String projectName) {
    JEditorPane dialogTextInfo = robot().finder().find(target(), Matchers.byType(JEditorPane.class));
    if (dialogTextInfo.getText().contains(projectName)) {
      return true;
    }
    return false;
  }

  private void waitForDialogToDisappear() {
    Wait.seconds(10).expecting(target().getTitle() + " dialog to disappear")
      .until(() -> !target().isShowing());
  }

  @NotNull
  @Override
  public JDialog target() {
    return myDialog;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myIdeFrameFixture.robot();
  }
}
