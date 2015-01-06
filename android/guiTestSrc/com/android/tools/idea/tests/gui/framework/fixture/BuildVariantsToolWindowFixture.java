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

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.variant.view.BuildVariantToolWindowFactory;
import com.intellij.ui.content.Content;
import org.fest.swing.cell.JTableCellReader;
import org.fest.swing.data.TableCell;
import org.fest.swing.data.TableCellFinder;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.data.TableCell.row;
import static org.junit.Assert.assertNotNull;

public class BuildVariantsToolWindowFixture extends ToolWindowFixture {
  @NotNull private final IdeFrameFixture myProjectFrame;

  public BuildVariantsToolWindowFixture(@NotNull IdeFrameFixture projectFrame) {
    super(BuildVariantToolWindowFactory.ID, projectFrame.getProject(), projectFrame.robot);
    myProjectFrame = projectFrame;
  }

  @NotNull
  public BuildVariantsToolWindowFixture select(@NotNull String module, @NotNull String variant) {
    activate();
    Content[] contents = myToolWindow.getContentManager().getContents();
    assertThat(contents.length).isGreaterThanOrEqualTo(1);

    Content content = contents[0];
    JTable variantsTable = myRobot.finder().findByType(content.getComponent(), JTable.class, true);

    final String moduleColumnText = "Module: '" + module + "'";

    JTableFixture table = new JTableFixture(myRobot, variantsTable);
    JTableCellFixture moduleCell = table.cell(new TableCellFinder() {
      @Override
      @Nullable
      public TableCell findCell(JTable table, JTableCellReader cellReader) {
        int rowCount = table.getRowCount();
        for (int i = 0; i < rowCount; i++) {
          int moduleColumnIndex = 0;
          String currentModule = cellReader.valueAt(table, i, moduleColumnIndex);
          if (moduleColumnText.equals(currentModule)) {
            return row(i).column(moduleColumnIndex);
          }
        }
        return null;
      }
    });
    assertNotNull("Failed to find module '" + module + "' in 'Build Variants' view", moduleCell);

    TableCell variantCellCoordinates = row(moduleCell.row()).column(1);
    String selectedVariant = table.valueAt(variantCellCoordinates);

    if (!variant.equals(selectedVariant)) {
      // Attempt to select variant if it is not already selected.
      JTableCellFixture variantCell = table.cell(variantCellCoordinates);
      variantCell.enterValue(variant);

      myProjectFrame.waitForBuildToFinish(BuildMode.SOURCE_GEN);
    }

    return this;
  }
}
