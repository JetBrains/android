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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JDialog;

public class SelectLibraryDependencyFixture implements ContainerFixture<JDialog> {

  private static final GenericTypeMatcher<JDialog> MATCHER = Matchers.byTitle(JDialog.class, "Choose Library Dependency");

  private final IdeFrameFixture ideFrameFixture;
  private final JDialog myDialog;
  private final Robot myRobot;

  @NotNull
  public static SelectLibraryDependencyFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    return new SelectLibraryDependencyFixture(ideFrameFixture, GuiTests.waitUntilShowing(ideFrameFixture.robot(), MATCHER));
  }

  private SelectLibraryDependencyFixture(@NotNull IdeFrameFixture ideFrame, @NotNull JDialog targetDialog) {
    this.ideFrameFixture = ideFrame;
    this.myDialog = targetDialog;
    this.myRobot = ideFrame.robot();
  }

  @NotNull
  @Override
  public JDialog target() {
    return myDialog;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myRobot;
  }

  @NotNull
  public SelectLibraryDependencyFixture selectDependency(@NotNull String partialMavenCoordinates) {
    new JTextComponentFixture(myRobot, myRobot.finder().findByType(myDialog, JBTextField.class))
      .click()
      .enterText(partialMavenCoordinates);
    new JButtonFixture(myRobot, myRobot.finder().findByType(FixedSizeButton.class)).click();

    JListFixture dependenciesList = new JListFixture(myRobot, myRobot.finder().findByType(myDialog, JBList.class));
    // network request made for the artifact list. Need to wait for cases of slow network
    Wait.seconds(60)
      .expecting(partialMavenCoordinates + " to be available in the library dependency list")
      .until(() -> getItemInArray(dependenciesList.contents(), partialMavenCoordinates) != null);

    String itemToClickOn = getItemInArray(dependenciesList.contents(), partialMavenCoordinates);

    if (itemToClickOn != null) {
      dependenciesList.clickItem(itemToClickOn);
      return this;
    } else {
      throw new ComponentLookupException("List item for " + partialMavenCoordinates + " suddenly went missing");
    }
  }

  @NotNull
  public IdeFrameFixture clickOK() {
    JButton button = myRobot.finder().find(myDialog, Matchers.byText(JButton.class, "OK"));
    GuiTask.execute(button::doClick);
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
    return ideFrameFixture;
  }

  @Nullable
  private String getItemInArray(@NotNull String[] strsToSearch, @NotNull String partialKey) {
    for (String item : strsToSearch) {
      if (item != null && item.contains(partialKey)) {
        return item;
      }
    }
    return null;
  }
}
