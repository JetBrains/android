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
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.variant.view.BuildVariantToolWindowFactory;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.ui.content.Content;
import org.fest.swing.cell.JTableCellReader;
import org.fest.swing.data.TableCell;
import org.fest.swing.data.TableCellFinder;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.data.TableCell.row;

public class BuildVariantsToolWindowFixture extends ToolWindowFixture {
  @NotNull private final IdeFrameFixture myProjectFrame;

  public BuildVariantsToolWindowFixture(@NotNull IdeFrameFixture projectFrame) {
    super(BuildVariantToolWindowFactory.ID, projectFrame.getProject(), projectFrame.robot());
    myProjectFrame = projectFrame;
  }

  @NotNull
  public BuildVariantsToolWindowFixture selectVariantForModule(@NotNull final String module, @NotNull String variant) {
    activate();
    Content[] contents = myToolWindow.getContentManager().getContents();
    assertThat(contents.length).isAtLeast(1);

    Content content = contents[0];
    JTable variantsTable = myRobot.finder().findByType(content.getComponent(), JTable.class, true);

    final String moduleColumnText = "Module: '" + module + "'";

    JTableFixture table = new JTableFixture(myRobot, variantsTable);
    JTableCellFixture moduleCell = table.cell(new TableCellFinder() {
      @Override
      @NotNull
      public TableCell findCell(@NotNull JTable table, @NotNull JTableCellReader cellReader) {
        int rowCount = table.getRowCount();
        for (int i = 0; i < rowCount; i++) {
          int moduleColumnIndex = 0;
          String currentModule = cellReader.valueAt(table, i, moduleColumnIndex);
          if (moduleColumnText.equals(currentModule)) {
            return row(i).column(moduleColumnIndex);
          }
        }
        throw new AssertionError("Failed to find module '" + module + "' in 'Build Variants' view");
      }
    });

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

  @NotNull
  public BuildVariantsToolWindowFixture selectTestArtifact(@NotNull String testArtifactDescription) {
    getTestArtifactComboBox().selectItem(testArtifactDescription);
    if (GradleProjectBuilder.getInstance(myProject).isSourceGenerationEnabled()) {
      myProjectFrame.waitForBuildToFinish(BuildMode.SOURCE_GEN);
    }
    GuiTests.waitForBackgroundTasks(myRobot);
    return this;
  }

  @NotNull
  private JComboBoxFixture getTestArtifactComboBox() {
    activate();
    Content[] contents = myToolWindow.getContentManager().getContents();
    assertThat(contents.length).isAtLeast(1);

    Content content = contents[0];
    JComboBox comboBox = myRobot.finder().findByType(content.getComponent(), JComboBox.class, true);
    return new JComboBoxFixture(myRobot, comboBox);
  }

  @Nullable
  public String getSelectedTestArtifact() {
    return getTestArtifactComboBox().selectedItem();
  }

  @NotNull
  public BuildVariantsToolWindowFixture selectUnitTests() {
    return this.selectTestArtifact("Unit Tests");
  }
}
