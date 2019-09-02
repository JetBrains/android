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

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForPopup;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.npw.BrowseSamplesWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.sdk.SdkProblemDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel;
import com.intellij.ui.components.JBList;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.jetbrains.annotations.NotNull;

public class WelcomeFrameFixture extends ComponentFixture<WelcomeFrameFixture, FlatWelcomeFrame> {

  @NotNull
  public static WelcomeFrameFixture find(@NotNull Robot robot) {
    WelcomeFrameFixture fixture = new WelcomeFrameFixture(robot, GuiTests.waitUntilShowing(robot, Matchers.byType(FlatWelcomeFrame.class)));
    fixture.target().toFront();
    return fixture;
  }

  @NotNull
  public static WelcomeFrameFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    return find(ideFrameFixture.robot());
  }

  private WelcomeFrameFixture(@NotNull Robot robot, @NotNull FlatWelcomeFrame target) {
    super(WelcomeFrameFixture.class, robot, target);
  }

  public SdkProblemDialogFixture createNewProjectWhenSdkIsInvalid() {
    findActionLinkByActionId("WelcomeScreen.CreateNewProject").click();
    return SdkProblemDialogFixture.find(this);
  }

  @NotNull
  public NewProjectWizardFixture createNewProject() {
    findActionLinkByActionId("WelcomeScreen.CreateNewProject").click();
    return NewProjectWizardFixture.find(robot());
  }

  @NotNull
  public FileChooserDialogFixture openProject() {
    findActionLinkByActionId("WelcomeScreen.OpenProject").click();
    return FileChooserDialogFixture.findDialog(robot(), "Open File or Project");
  }

  @NotNull
  public WelcomeFrameFixture importProject() {
    findActionLinkByActionId("WelcomeScreen.ImportProject").click();
    return this;
  }

  @NotNull
  public FileChooserDialogFixture profileOrDebugApk() {
    findActionLinkByActionId("WelcomeScreen.AndroidStudio.apkProfilingAndDebugging").click();
    return FileChooserDialogFixture.findDialog(robot(), "Select APK File");
  }

  @NotNull
  public BrowseSamplesWizardFixture importCodeSample() {
    findActionLinkByActionId("WelcomeScreen.GoogleCloudTools.SampleImport").click();
    return BrowseSamplesWizardFixture.find(robot());
  }

  @NotNull
  public IdeFrameFixture openTheMostRecentProject(@NotNull GuiTestRule guiTestRule) {
    RecentProjectPanel recentProjectPanel = robot().finder().findByType(RecentProjectPanel.class);
    JBList jbList = robot().finder().findByType(recentProjectPanel, JBList.class, true);

    if (!jbList.isEmpty()) {
      JListFixture listFixture= new JListFixture(robot(), jbList);
      listFixture.clickItem(0);
    }

    return guiTestRule.ideFrame();
  }

  @NotNull
  public JListFixture clickConfigure() {
    ActionLinkFixture.findByActionText("Configure", robot(), target()).click();
    return new JListFixture(robot(), waitForPopup(robot()));
  }

  public void openSdkManager(@NotNull JListFixture listFixture) {
    listFixture.clickItem("SDK Manager");
  }

  @NotNull
  private ActionLinkFixture findActionLinkByActionId(String actionId) {
    return ActionLinkFixture.findByActionId(actionId, robot(), target());
  }
}
