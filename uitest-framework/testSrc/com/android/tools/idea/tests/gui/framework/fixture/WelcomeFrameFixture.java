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
import com.android.tools.idea.tests.gui.framework.fixture.npw.BrowseSamplesWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.sdk.SdkProblemDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

public class WelcomeFrameFixture extends ComponentFixture<WelcomeFrameFixture, FlatWelcomeFrame> {

  @NotNull
  public static WelcomeFrameFixture find(@NotNull Robot robot) {
    return new WelcomeFrameFixture(robot, GuiTests.waitUntilShowing(robot, Matchers.byType(FlatWelcomeFrame.class)));
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
  public WelcomeFrameFixture importProject() {
    findActionLinkByActionId("WelcomeScreen.ImportProject").click();
    return this;
  }

  @NotNull
  public WelcomeFrameFixture profileDebugApk() {
    findActionLinkByActionId("WelcomeScreen.AndroidStudio.apkProfilingAndDebugging").click();
    return this;
  }

  @NotNull
  public BrowseSamplesWizardFixture importCodeSample() {
    findActionLinkByActionId("WelcomeScreen.GoogleCloudTools.SampleImport").click();
    return BrowseSamplesWizardFixture.find(robot());
  }

  @NotNull
  private ActionLinkFixture findActionLinkByActionId(String actionId) {
    return ActionLinkFixture.findByActionId(actionId, robot(), target());
  }
}
