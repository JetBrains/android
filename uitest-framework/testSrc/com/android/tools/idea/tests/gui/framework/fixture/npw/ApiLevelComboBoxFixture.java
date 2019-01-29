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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import org.fest.swing.core.Robot;
import org.fest.swing.driver.BasicJComboBoxCellReader;
import org.fest.swing.driver.JComboBoxDriver;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.LocationUnavailableException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ApiLevelComboBoxFixture {

  private final Robot myRobot;
  private final JComboBox myTarget;

  ApiLevelComboBoxFixture(@NotNull Robot robot, @NotNull JComboBox target) {
    myRobot = robot;
    myTarget = target;
  }

  public void selectApiLevel(@NotNull String apiLevel) {
    int itemIndex = GuiQuery.getNonNull(
      () -> {
        BasicJComboBoxCellReader cellReader = new BasicJComboBoxCellReader();
        int itemCount = myTarget.getItemCount();
        for (int i = 0; i < itemCount; i++) {
          String value = cellReader.valueAt(myTarget, i);
          if (value != null && value.startsWith("API " + apiLevel + ":")) {
            return i;
          }
        }
        return -1;
      });
    if (itemIndex < 0) {
      throw new LocationUnavailableException("Unable to find SDK " + apiLevel + " in drop-down");
    }
    JComboBoxDriver comboBoxDriver = new JComboBoxDriver(myRobot);
    comboBoxDriver.selectItem(myTarget, itemIndex);
  }

}
