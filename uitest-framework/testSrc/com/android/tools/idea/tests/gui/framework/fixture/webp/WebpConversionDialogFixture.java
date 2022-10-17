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
package com.android.tools.idea.tests.gui.framework.fixture.webp;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

import com.android.tools.idea.rendering.webp.WebpConversionDialog;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JRadioButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class WebpConversionDialogFixture extends IdeaDialogFixture<WebpConversionDialog> {

  @NotNull
  public static WebpConversionDialogFixture findDialog(@NotNull Robot robot) {
    return findDialog(robot, Matchers.byTitle(JDialog.class, "Converting Images to WebP"));
  }

  @NotNull
  public static WebpConversionDialogFixture findDialog(@NotNull Robot robot, @NotNull final GenericTypeMatcher<JDialog> matcher) {
    return new WebpConversionDialogFixture(robot, find(robot, WebpConversionDialog.class, matcher));
  }

  private WebpConversionDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<WebpConversionDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public WebpConversionDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }

  public WebpConversionDialogFixture selectLossy() {
    clickRadioButton("Lossy encoding");
    return this;
  }

  public WebpConversionDialogFixture selectLossless() {
    clickRadioButton("Lossless encoding");
    return this;
  }

  private void clickRadioButton(String text) {
    Robot robot = robot();
    JRadioButton checkbox = robot.finder().find(target(), Matchers.byText(JRadioButton.class, text));
    Wait.seconds(1).expecting("button " + text + " to be enabled")
      .until(() -> checkbox.isEnabled() && checkbox.isVisible() && checkbox.isShowing());
    if (!checkbox.isSelected()) {
      robot.click(checkbox);
    }
  }

  @NotNull
  public JCheckBox getCheckBox(@NotNull String text) {
    JCheckBox checkbox = robot().finder().find(target(), Matchers.byText(JCheckBox.class, text));
    Wait.seconds(1).expecting("checkbox " + text + " to be enabled")
      .until(() -> checkbox.isVisible() && checkbox.isShowing());
    return checkbox;
  }
}
