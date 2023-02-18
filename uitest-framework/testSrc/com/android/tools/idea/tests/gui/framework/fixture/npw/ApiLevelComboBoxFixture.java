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

import javax.swing.JComboBox;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

public class ApiLevelComboBoxFixture extends JComboBoxFixture {

  ApiLevelComboBoxFixture(@NotNull Robot robot, @NotNull JComboBox target) {
    super(robot, target);
  }

  public void selectApiLevel(int minSdkApi) {
    GuiTask.execute(() -> {
      for (int i = 0; i < target().getItemCount(); i++) {
        Object value = target().getItemAt(i);
        if (String.valueOf(value).startsWith("API " + minSdkApi + " (")) {
          // The comboBox fixture is un-reliable selecting the right API. Select by value instead.
          target().setSelectedItem(value);
          return;
        }
      }
      throw new LocationUnavailableException("Unable to find SDK " + minSdkApi + " in drop-down");
    });
  }
}
