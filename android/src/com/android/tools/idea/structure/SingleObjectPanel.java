/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.structure;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.List;

public class SingleObjectPanel extends BuildFilePanel {
  protected final GrClosableBlock myRoot;
  protected final List<BuildFileKey> myProperties;
  private final JBTable myTable;
  @Nullable private final SingleObjectTableModel myModel;

  public SingleObjectPanel(@NotNull Project project, @NotNull String moduleName, @Nullable GrClosableBlock root,
                           @NotNull List<BuildFileKey> properties) {
    super(project, moduleName);
    myRoot = root;
    myProperties = properties;

    myModel = myGradleBuildFile != null ? new SingleObjectTableModel(myGradleBuildFile, myRoot, myProperties) : null;

    // We have to provide our own cell editors because JTable by default only allows one data type per column; we vary our
    // data type by row.
    myTable = new JBTable(myGradleBuildFile != null ? myModel : new DefaultTableModel()) {
      @Override
      public TableCellEditor getCellEditor(int row, int col) {
        TableCellEditor editor = myModel != null ? myModel.getCellEditor(row, col) : null;
        return editor != null ? editor : super.getCellEditor(row, col);
      }
    };
    myTable.setShowColumns(false);
    myTable.setShowGrid(false);
    myTable.setDragEnabled(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setCellSelectionEnabled(false);
  }

  @Override
  protected void addItems(@NotNull JPanel parent) {
    add(myTable, BorderLayout.CENTER);

    if (myTable.getRowCount() > 0) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  @Override
  public void apply() {
    if (myModel != null) {
      myModel.apply();
    }
  }

  @Override
  public boolean isModified() {
    return myModel != null && myModel.isModified();
  }
}
