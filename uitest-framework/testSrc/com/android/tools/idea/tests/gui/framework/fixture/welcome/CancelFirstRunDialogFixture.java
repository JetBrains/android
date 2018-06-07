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
package com.android.tools.idea.tests.gui.framework.fixture.welcome;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CancelFirstRunDialogFixture extends AbstractWizardFixture<CancelFirstRunDialogFixture> {
  CancelFirstRunDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(CancelFirstRunDialogFixture.class, robot, target);
  }

  @NotNull
  public CancelFirstRunDialogFixture selectRerunOnNextStartup() {
    clickRadioButton("Re-run the setup wizard on the next Android Studio startup (Recommended)");
    return this;
  }

  @NotNull
  public CancelFirstRunDialogFixture selectDoNotRerunOnNextStartup() {
    clickRadioButton("Do not re-run the setup wizard");
    return this;
  }

  @NotNull
  public CancelFirstRunDialogFixture clickOK() {
    GuiTests.findAndClickOkButton(this);
    return this;
  }

  private void clickRadioButton(String text) {
    JRadioButton radioButton = robot().finder().find(target(), Matchers.byText(JRadioButton.class, text));
    robot().click(radioButton);
  }
}
