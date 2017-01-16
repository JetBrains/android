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

import com.android.tools.idea.gradle.project.subset.ModulesToImportDialog;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ModulesToImportDialogFixture extends IdeaDialogFixture<ModulesToImportDialog> {

  private ModulesToImportDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<ModulesToImportDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public static ModulesToImportDialogFixture find(@NotNull Robot robot) {
    return new ModulesToImportDialogFixture(robot, find(robot, ModulesToImportDialog.class));
  }

  @NotNull
  public JTableFixture getModuleTable() {
    return new JTableFixture(robot(), robot().finder().findByType(target(), JTable.class, true));
  }

  @NotNull
  public ImmutableList<String> getModuleList() {
    JTableFixture moduleTable = getModuleTable();
    int size = moduleTable.rowCount();
    String[][] contents = moduleTable.contents();
    ImmutableList.Builder<String> result = ImmutableList.builder();
    for (int i = 0; i < size; i++) {
      result.add(contents[i][1]);
    }
    return result.build();
  }

  @NotNull
  public ImmutableList<String> getSelectedModuleList() {
    JTableFixture moduleTable = getModuleTable();
    int size = moduleTable.rowCount();
    String[][] contents = moduleTable.contents();
    ImmutableList.Builder<String> result = ImmutableList.builder();
    for (int i = 0; i < size; i++) {
      if ("true".equals(contents[i][0])) {
        result.add(contents[i][1]);
      }
    }
    return result.build();
  }

  @NotNull
  public ModulesToImportDialogFixture setSelected(@NotNull String moduleName, boolean selected) {
    JTableFixture moduleTable = getModuleTable();
    JTableCellFixture cell = moduleTable.cell(moduleName);
    moduleTable.enterValue(TableCell.row(cell.row()).column(0), String.valueOf(selected));
    return this;
  }

  @NotNull
  public ModulesToImportDialogFixture loadSelectionFromFile(@NotNull VirtualFile targetFile) {
    ActionButtonFixture.findByText("Select All", robot(), target()).click();
    ActionButtonFixture.findByText("Load Selection from File", robot(), target()).click();
    FileChooserDialogFixture.findDialog(robot(), Matchers.byTitle(JDialog.class, "Load Module Selection"))
      .select(targetFile)
      .clickOk();
    return this;
  }

  @NotNull
  public ModulesToImportDialogFixture saveSelectionToFile(@NotNull VirtualFile targetFile) {
    ActionButtonFixture.findByText("Save Selection As", robot(), target()).click();
    FileChooserDialogFixture.findDialog(robot(), Matchers.byTitle(JDialog.class, "Save Module Selection"))
      .select(targetFile)
      .clickOk();

    // "Confirm save" dialog will pop up because the file already exists, we click on Yes to continue.
    MessagesFixture.findByTitle(robot(), "Confirm Save as").click("Yes");
    return this;
  }

  public void clickOk() {
    GuiTests.findAndClickOkButton(this);
    Wait.seconds(1).expecting("dialog to disappear").until(() -> !target().isShowing());
  }

  public ModulesToImportDialogFixture doQuickSearch(@NotNull String string) {
    robot().enterText(string, target());
    return this;
  }
}
