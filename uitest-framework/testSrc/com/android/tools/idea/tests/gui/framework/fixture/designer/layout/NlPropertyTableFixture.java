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
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import com.android.tools.adtui.ptable.PTable;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @NotNull
  public NlPropertyTableFixture adjustIdeFrameHeightToShowNumberOfRow(int rowCount) {
    Component table = target();
    assertThat(table).isNotNull();
    int height = target().getRowHeight();
    Container previousParent = null;
    Container parent = table.getParent();
    int adjustment = 0;
    while (parent != null) {
      if (adjustment == 0 && parent instanceof JScrollPane) {
        adjustment = rowCount * height - parent.getHeight();
      }
      previousParent = parent;
      parent = parent.getParent();
    }
    assertThat(previousParent).isNotNull();
    Dimension size = previousParent.getSize();
    size.height += adjustment;
    previousParent.setSize(size);
    return this;
  }

  @Nullable
  public NlPropertiesPanel getParentPropertiesPanel() {
    Component c = target().getParent();
    while (c != null) {
      Component parent = c.getParent();
      if (parent instanceof NlPropertiesPanel) {
        return (NlPropertiesPanel)parent;
      }
      c = parent;
    }
    return null;
  }
}
