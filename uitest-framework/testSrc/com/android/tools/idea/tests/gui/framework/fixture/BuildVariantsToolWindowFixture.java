/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.tools.idea.tests.gui.framework.UiTestUtilsKt.waitForIdle;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.data.TableCell.row;

import com.android.tools.idea.gradle.variant.view.BuildVariantToolWindowFactory;
import com.intellij.ui.content.Content;
import javax.swing.JList;
import javax.swing.JTable;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

public class BuildVariantsToolWindowFixture extends ToolWindowFixture {
  @NotNull private final IdeFrameFixture myProjectFrame;

  public BuildVariantsToolWindowFixture(@NotNull IdeFrameFixture projectFrame) {
    super(BuildVariantToolWindowFactory.ID, projectFrame.getProject(), projectFrame.robot());
    myProjectFrame = projectFrame;
  }

  @NotNull
  public BuildVariantsToolWindowFixture selectVariantForModule(@NotNull final String module, @NotNull String variant) {
    JTableFixture table = getJTableFixture();

    JTableCellFixture moduleCell = getModuleCell(table, module);
    TableCell variantCellCoordinates = row(moduleCell.row()).column(1);
    String selectedVariant = table.valueAt(variantCellCoordinates);

    if (!variant.equals(selectedVariant)) {
      // Attempt to select variant if it is not already selected.
      JTableCellFixture variantCell = table.cell(variantCellCoordinates);
      variantCell.startEditing();
      waitForIdle();
      new JListFixture(robot(), robot().finder().findByType(robot().findActivePopupMenu(), JList.class)).selectItem(variant);
      waitForIdle();
    }

    return this;
  }

  public JTableCellFixture getModuleCell(String module) {

    final String moduleColumnText = "Module: '" + module + "'";
    JTableFixture table = getJTableFixture();
    return table.cell(
      (jTable, cellReader) -> {
        int rowCount = jTable.getRowCount();
        for (int i = 0; i < rowCount; i++) {
          int moduleColumnIndex = 0;
          String currentModule = cellReader.valueAt(jTable, i, moduleColumnIndex);
          if (moduleColumnText.equals(currentModule)) {
            return row(i).column(moduleColumnIndex);
          }
        }
        throw new AssertionError("Failed to find module '" + module + "' in 'Build Variants' view");
      });
  }

  public JTableCellFixture getModuleCell(JTableFixture table, String module) {
    final String moduleColumnText = "Module: '" + module + "'";
    return table.cell(
      (jTable, cellReader) -> {
        int rowCount = jTable.getRowCount();
        for (int i = 0; i < rowCount; i++) {
          int moduleColumnIndex = 0;
          String currentModule = cellReader.valueAt(jTable, i, moduleColumnIndex);
          if (moduleColumnText.equals(currentModule)) {
            return row(i).column(moduleColumnIndex);
          }
        }
        throw new AssertionError("Failed to find module '" + module + "' in 'Build Variants' view");
      });
  }

  @NotNull
  public JTableFixture getJTableFixture() {
    activate();
    Content[] contents = myToolWindow.getContentManager().getContents();
    assertThat(contents.length).isAtLeast(1);

    Content content = contents[0];
    JTable variantsTable = myRobot.finder().findByType(content.getComponent(), JTable.class, true);
    return new JTableFixture(myRobot, variantsTable);
  }
}
