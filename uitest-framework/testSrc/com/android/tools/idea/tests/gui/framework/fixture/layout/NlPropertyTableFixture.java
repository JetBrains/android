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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.property.ptable.PTable;
import com.intellij.openapi.wm.IdeFocusManager;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;

import java.awt.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.google.common.truth.Truth.assertThat;

public class NlPropertyTableFixture extends JTableFixture {

  public NlPropertyTableFixture(@Nonnull Robot robot, @Nonnull PTable target) {
    super(robot, target);
  }

  @NotNull
  public static NlPropertyTableFixture create(@NotNull Robot robot) {
    PTable table = waitUntilFound(robot, Matchers.byType(PTable.class));
    return new NlPropertyTableFixture(robot, table);
  }

  public NlPropertyTableFixture waitForMinimumRowCount(int minimumRowCount, @NotNull Wait waitForRowCount) {
    waitForRowCount.expecting("table data to load").until(() -> target().getRowCount() > minimumRowCount);
    return this;
  }

  public NlPropertyTableFixture type(char character) {
    robot().type(character, target());
    return this;
  }
}
