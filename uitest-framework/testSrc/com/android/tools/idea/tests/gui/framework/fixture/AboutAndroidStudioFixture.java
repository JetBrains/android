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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBLabel;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.jetbrains.annotations.NotNull;

public class AboutAndroidStudioFixture implements ContainerFixture<JDialog> {
  @NotNull private final IdeFrameFixture myIdeFrameFixture;
  @NotNull private final JDialog myDialog;

  private static void openAboutAndroidStudio(@NotNull IdeFrameFixture ideFrameFixture) {
    if (SystemInfo.isMac) {
      ideFrameFixture.invokeMenuPath("Android Studio", "About Android Studio");
    }
    else {
      ideFrameFixture.invokeMenuPath("Help", "About");
    }
  }

  @NotNull
  public static AboutAndroidStudioFixture openAboutStudioDialog(IdeFrameFixture ideFrameFixture) {
    String title = "About Android Studio";
    openAboutAndroidStudio(ideFrameFixture);
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(),
                                               Matchers.byTitle(JDialog.class, title));
    return new AboutAndroidStudioFixture(ideFrameFixture, dialog);
  }

  private AboutAndroidStudioFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myDialog = dialog;
  }

  private List<JBLabel> getAllLabels() {
    return Lists.newArrayList(robot().finder().findAll(Matchers.byType(JBLabel.class).andIsShowing()));
  }

  public String getAndroidStudioVersion() {
    List<JBLabel> allLabels = getAllLabels();
    String name = allLabels.get(0).getText();
    return name;
  }

  public IdeFrameFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    return myIdeFrameFixture;
  }

  public void clickCopy() {
    JButton button = GuiTests.waitUntilShowing(robot(), Matchers.byText(JButton.class, "Copy").andIsEnabled());
    robot().click(button);
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