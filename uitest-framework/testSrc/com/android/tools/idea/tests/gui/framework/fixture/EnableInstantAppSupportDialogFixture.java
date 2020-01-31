/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

import com.android.tools.idea.gradle.actions.EnableInstantAppsSupportDialog;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

public class EnableInstantAppSupportDialogFixture extends IdeaDialogFixture<EnableInstantAppsSupportDialog> {
  @NotNull
  public static EnableInstantAppSupportDialogFixture find(@NotNull IdeFrameFixture frameFixture) {
    return new EnableInstantAppSupportDialogFixture(frameFixture.robot(), find(frameFixture.robot(), EnableInstantAppsSupportDialog.class));
  }

  private EnableInstantAppSupportDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<EnableInstantAppsSupportDialog> dialog) {
    super(robot, dialog);
  }

  public void clickOk() {
    findAndClickOkButton(this);
    waitUntilNotShowing();
  }
}