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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ThreeState;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.*;
import com.android.tools.idea.run.AvdComboBox;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.junit.Assert.assertNotNull;

public class ChooseDeviceDialogFixture extends ComponentFixture<ChooseDeviceDialogFixture, JDialog>
  implements ContainerFixture<JDialog> {

  @NotNull
  public static ChooseDeviceDialogFixture find(@NotNull Robot robot) {
    Dialog dialog = WindowFinder.findDialog(DialogMatcher.withTitle(AndroidBundle.message("choose.device.dialog.title")))
                                .withTimeout(TimeUnit.MINUTES.toMillis(2)).using(robot)
                                .target();
    assertThat(dialog).isInstanceOf(JDialog.class);
    return new ChooseDeviceDialogFixture(robot, (JDialog)dialog);
  }

  private ChooseDeviceDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(ChooseDeviceDialogFixture.class, robot, target);
  }

  @NotNull
  protected JButton findButtonByText(@NotNull String text) {
    return robot().finder().find(target(), withText(text).andShowing());
  }

  private boolean chooseRunningDeviceStep(@NotNull String deviceName) {
    new JRadioButtonFixture(robot(), findRadioButtonByText("Choose a running device")).click();

    JBTable deviceTable = robot().finder().findByType(target(), JBTable.class);
    assertNotNull(deviceTable);
    JTableFixture deviceTableFixture = new JTableFixture(robot(), deviceTable);

    int deviceColumnIndex = deviceTable.getColumn("Device").getModelIndex();
    int compatibleColumnIndex = deviceTable.getColumn("Compatible").getModelIndex();

    int rowToSelect = -1;

    for (int i = 0; i < deviceTable.getRowCount(); ++i) {
      IDevice device = (IDevice)deviceTable.getModel().getValueAt(i, deviceColumnIndex);
      ThreeState launchCompatibility = ((LaunchCompatibility)deviceTable.getModel().getValueAt(i, compatibleColumnIndex)).isCompatible();

      // Only run devices if they're what's specified and are ready/compatible.
      if (device.getAvdName() != null &&
          device.getAvdName().equals(deviceName) &&
          device.getState() == IDevice.DeviceState.ONLINE &&
          launchCompatibility == ThreeState.YES) {
        rowToSelect = i;
        break;
      }
    }

    if (rowToSelect < 0) {
      return false;
    }
    deviceTableFixture.selectRows(rowToSelect);
    return true;
  }

  @NotNull
  public ChooseDeviceDialogFixture getChooseDeviceDialog(@NotNull String deviceName) {
    JRadioButtonFixture launchEmulatorButton = new JRadioButtonFixture(robot(), findRadioButtonByText("Launch emulator"));
    launchEmulatorButton.click();

    AvdComboBox avdComboBox = robot().finder().findByType(AvdComboBox.class);
    JComboBoxFixture comboBox = new JComboBoxFixture(robot(), avdComboBox.getComboBox());
    comboBox.requireNotEditable()
            .selectItem(deviceName)
            .requireSelection(deviceName);

    return this;
  }

  @NotNull
  private JRadioButton findRadioButtonByText(@NotNull final String text) {
    return robot().finder().find(target(), new GenericTypeMatcher<JRadioButton>(JRadioButton.class) {
      @Override
      protected boolean isMatching(@NotNull JRadioButton button) {
        return text.equals(button.getText()) && button.isShowing();
      }
    });
  }

  @NotNull
  public ChooseDeviceDialogFixture selectUseSameDeviceStep(boolean value) {
    JCheckBoxFixture useSameDeviceCheckBox = new JCheckBoxFixture(robot(), "Use same device for future launches");
    useSameDeviceCheckBox.setSelected(value);
    return this;
  }

  @NotNull
  public ChooseDeviceDialogFixture selectEmulator(@NotNull String emulatorName) {
    // Try to find already-running emulators to launch the app on.
    if (!chooseRunningDeviceStep(emulatorName)) {
      // If we can't find an already-launched device, fire up a new one.
      getChooseDeviceDialog(emulatorName);
    }
    return this;
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }

  public void clickCancel() {
    findAndClickCancelButton(this);
  }
}
