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
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.AnimatedIcon;
import org.fest.swing.cell.JListCellReader;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilGone;
import static com.google.common.truth.Truth.assertThat;

/** Fixture for {@link com.android.tools.idea.run.editor.DeployTargetPickerDialog}. */
public class DeployTargetPickerDialogFixture extends ComponentFixture<DeployTargetPickerDialogFixture, JDialog>
  implements ContainerFixture<JDialog> {

  private Dialog dialogContainer;

  @NotNull
  public static DeployTargetPickerDialogFixture find(@NotNull Robot robot) {
    Dialog dialog = WindowFinder.findDialog(DialogMatcher.withTitle(AndroidBundle.message("choose.device.dialog.title")).andShowing())
      .withTimeout(TimeUnit.SECONDS.toMillis(5)).using(robot)
      .target();
    assertThat(dialog).isInstanceOf(JDialog.class);
    // When adb & avd manager are not initialized, animated icons are displayed. We wait until those animated icons disappear.
    waitUntilGone(robot, dialog, new GenericTypeMatcher<AnimatedIcon>(AnimatedIcon.class) {
      @Override
      protected boolean isMatching(@NotNull AnimatedIcon component) {
        return component.isRunning();
      }
    }, 10);
    return new DeployTargetPickerDialogFixture(robot, (JDialog)dialog);
  }

  private DeployTargetPickerDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(DeployTargetPickerDialogFixture.class, robot, target);
    dialogContainer = (Dialog)target;
  }

  /**
   * This looks odd because {@code DevicePicker} uses {@code AndroidDeviceRenderer}, which is a {@code ColoredListCellRenderer}, whose
   * {@code getListCellRendererComponent} method returns {@code this}, which is also a {@code SimpleColoredComponent}, whose
   * {@code toString} method returns what gets rendered to the cell. You may vomit now.
   */
  private static final JListCellReader DEVICE_PICKER_CELL_READER = (jList, index) -> {
    String item =
      jList.getCellRenderer().getListCellRendererComponent(jList, jList.getModel().getElementAt(index), index, true, true).toString();
    // Ignore everything after the parenthesis (typically includes stuff like API level and ABI)
    return item.split("\\(", 2)[0].trim();
  };

  /**
   *  Selects a device whose entry in the devices list is {@code text}. Any characters after the first parenthesis are ignored.
   */
  @NotNull
  public DeployTargetPickerDialogFixture selectDevice(String text) {
    JBList deviceList = robot().finder().findByType(target(), JBList.class);
    JListFixture jListFixture = new JListFixture(robot(), deviceList);
    jListFixture.replaceCellReader(DEVICE_PICKER_CELL_READER);
    Wait.seconds(15).expecting(String.format("Deployment Target list to contain %s", text))
      .until(() -> Arrays.asList(jListFixture.contents()).contains(text));
    jListFixture.selectItem(text);
    return this;
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }

  public void clickHelp() {
    Robot robot = this.robot();
    new JButtonFixture(robot, GuiTests.waitUntilShowingAndEnabled(robot, this.target(), Matchers.byText(JButton.class, "Help"))).click();
  }

  @NotNull
  public JButtonFixture getCreateNewVirtualDeviceButton() {
    return new JButtonFixture(
        robot(),
        robot().finder().find(dialogContainer, Matchers.byText(JButton.class, "Create New Virtual Device")));
  }
}
