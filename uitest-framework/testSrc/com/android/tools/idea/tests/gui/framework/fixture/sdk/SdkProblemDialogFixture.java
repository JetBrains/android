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
package com.android.tools.idea.tests.gui.framework.fixture.sdk;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SdkProblemDialogFixture<ParentFixture extends ComponentFixture> implements ContainerFixture<JDialog> {

  @NotNull
  public static <ParentFixture extends ComponentFixture> SdkProblemDialogFixture find(ParentFixture fixture) {
    JDialog dialog = GuiTests.waitUntilShowing(fixture.robot(), Matchers.byTitle(JDialog.class, "SDK Problem"));
    return new SdkProblemDialogFixture<>(fixture, dialog);
  }

  private final ParentFixture myParent;
  private final JDialog myDialog;

  private SdkProblemDialogFixture(@NotNull ParentFixture parent, JDialog dialog) {
    myParent = parent;
    myDialog = dialog;
  }

  @NotNull
  public IdeSettingsDialogFixture openSDKManager() {
    robot().click(robot().finder().find(target(), Matchers.byText(JButton.class, "Open SDK Manager")));
    Wait.seconds(1).expecting("dialog to disappear").until(() -> !target().isShowing());
    return IdeSettingsDialogFixture.find(robot());
  }

  @NotNull
  @Override
  public JDialog target() {
    return myDialog;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myParent.robot();
  }
}
