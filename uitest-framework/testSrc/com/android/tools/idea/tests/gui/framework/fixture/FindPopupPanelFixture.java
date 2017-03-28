/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.intellij.find.impl.FindPopupPanel;
import org.fest.swing.fixture.JPanelFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class FindPopupPanelFixture extends JPanelFixture {
  private final IdeFrameFixture myIdeFrameFixture;

  public FindPopupPanelFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JPanel target) {
    super(ideFrameFixture.robot(), target);
    myIdeFrameFixture = ideFrameFixture;
  }

  @NotNull
  public static FindPopupPanelFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    return new FindPopupPanelFixture(ideFrameFixture,
                                     GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byType(FindPopupPanel.class)));
  }

  @NotNull
  public FindPopupPanelFixture setTextToFind(@NotNull final String text) {
    new JTextComponentFixture(robot(), GuiTests.waitUntilShowing(robot(), target(), Matchers.byType(JTextArea.class))).click()
      .enterText(text);
    return this;
  }

  @NotNull
  public FindToolWindowFixture.ContentFixture clickFind() {
    GuiTests.findAndClickButton(this, "Open in Find Window");
    GuiTests.waitForBackgroundTasks(robot());
    return new FindToolWindowFixture.ContentFixture(myIdeFrameFixture);
  }
}
