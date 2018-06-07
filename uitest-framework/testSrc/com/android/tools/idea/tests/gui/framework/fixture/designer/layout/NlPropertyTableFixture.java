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

import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCellFinder;
import org.fest.swing.driver.JTableDriver;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.AbstractJPopupMenuInvokerFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.google.common.truth.Truth.assertThat;

public class NlPropertyTableFixture extends AbstractJPopupMenuInvokerFixture<NlPropertyTableFixture, PTable, JTableDriver> {
  private static final int POPULATE_TABLE_TIMEOUT_SECONDS = 5;

  private final JTableFixture myTableFixture;

  private NlPropertyTableFixture(@Nonnull Robot robot, @Nonnull PTable target) {
    super(NlPropertyTableFixture.class, robot, target);
    myTableFixture = new JTableFixture(robot, target);
  }

  @NotNull
  public static NlPropertyTableFixture create(@NotNull Robot robot) {
    PTable table = waitUntilFound(robot, Matchers.byType(PTable.class));
    return new NlPropertyTableFixture(robot, table);
  }

  @Override
  @NotNull
  protected JTableDriver createDriver(@NotNull Robot robot) {
    return new JTableDriver(robot);
  }

  public NlPropertyTableFixture waitForMinimumRowCount(int minimumRowCount) throws Exception {
    Wait.seconds(POPULATE_TABLE_TIMEOUT_SECONDS).expecting("table data to load").until(() -> target().getRowCount() > minimumRowCount);
    return this;
  }

  @NotNull
  public NlPropertyTableFixture type(char character) {
    robot().type(character, target());
    return this;
  }

  @NotNull
  public NlPropertyTableFixture enterText(@NotNull String text) {
    robot().enterText(text);
    return this;
  }

  @NotNull
  public NlPropertyTableFixture tab() {
    robot().pressAndReleaseKey(KeyEvent.VK_TAB);
    return this;
  }

  @NotNull
  public NlPropertyTableFixture requireContent(@NotNull String key, @Nullable String value) {
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        PTableItem item = target().getItemAt(target().getSelectedRow());
        assertThat(item.getName()).isEqualTo(key);
        assertThat(item.getValue()).isEqualTo(value);
      }
    });
    return this;
  }

  @Nullable
  private static String getItemValue(@NotNull PTableItem item) {
    return GuiActionRunner.execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return item.getValue();
      }
    });
  }

  /**
   * Sets the frame height such that we see only the requested number of properties
   * in the property table.
   *
   * @param frameFixture the frame to set the size of
   * @param rowCount the wanted number of visible properties in the inspector
   * @return this fixture
   */
  @NotNull
  @SuppressWarnings("UnusedReturnValue")
  public NlPropertyTableFixture adjustIdeFrameHeightToShowNumberOfRows(@NotNull IdeFrameFixture frameFixture, int rowCount) {
    Component table = target();
    assertThat(table).isNotNull();
    int height = target().getRowHeight();
    Container parent = SwingUtilities.getAncestorOfClass(JScrollPane.class, target());
    int adjustment = rowCount * height - parent.getHeight();

    Dimension size = frameFixture.getIdeFrameSize();
    Dimension newSize = new Dimension(size.width, size.height + adjustment);
    frameFixture.setIdeFrameSize(newSize);
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

  // It would be nice if we could extend JTableFixture and still get NlPropertyTableFixture as the return type for the methods.
  // Then we could avoid the following redirects.

  @NotNull
  public JTableCellFixture cell(@NotNull TableCellFinder cellFinder) {
    return myTableFixture.cell(cellFinder);
  }

  @NotNull
  public NlPropertyTableFixture selectRows(@NotNull int... rows) {
    myTableFixture.selectRows(rows);
    return this;
  }

  public int rowCount() {
    return myTableFixture.rowCount();
  }

  @NotNull
  @SuppressWarnings("UnusedReturnValue")
  public NlPropertyTableFixture requireSelectedRows(@NotNull int... rows) {
    myTableFixture.requireSelectedRows(rows);
    return this;
  }
}
