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
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

public class WelcomeFrameFixture extends ComponentFixture<WelcomeFrameFixture, FlatWelcomeFrame> {

  @NotNull
  public static WelcomeFrameFixture find(@NotNull Robot robot) {
    return new WelcomeFrameFixture(robot, GuiTests.waitUntilShowing(robot, GuiTests.matcherForType(FlatWelcomeFrame.class)));
  }

  @NotNull
  public static WelcomeFrameFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    return find(ideFrameFixture.robot());
  }

  private WelcomeFrameFixture(@NotNull Robot robot, @NotNull FlatWelcomeFrame target) {
    super(WelcomeFrameFixture.class, robot, target);
  }

  @NotNull
  public WelcomeFrameFixture createNewProject() {
    findActionLinkByActionId("WelcomeScreen.CreateNewProject").click();
    return this;
  }

  @NotNull
  public WelcomeFrameFixture importProject() {
    findActionLinkByActionId("WelcomeScreen.ImportProject").click();
    return this;
  }

  @NotNull
  private ActionLinkFixture findActionLinkByActionId(String actionId) {
    return ActionLinkFixture.findByActionId(actionId, robot(), target());
  }

  @NotNull
  public MessagesFixture findMessageDialog(@NotNull String title) {
    return MessagesFixture.findByTitle(robot(), target(), title);
  }
}
