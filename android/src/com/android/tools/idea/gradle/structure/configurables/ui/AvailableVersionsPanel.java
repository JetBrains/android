/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.android.ide.common.repository.GradleVersion;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

class AvailableVersionsPanel extends JPanel {
  private final TableView<GradleVersion> myVersionsTable;

  AvailableVersionsPanel(Consumer<String> notifyVersionSelectionChanged) {
    super(new BorderLayout());
    myVersionsTable = new TableView<>();
    myVersionsTable.setShowGrid(false);
    myVersionsTable.setSelectionMode(SINGLE_SELECTION);
    myVersionsTable.getListTableModel().setColumnInfos(new ColumnInfo[] {
      new ColumnInfo<GradleVersion, String>("Versions") {
        @Override
        @Nullable
        public String valueOf(GradleVersion version) {
          return version.toString();
        }
      }
    });

    myVersionsTable.getSelectionModel().addListSelectionListener(e -> {
      GradleVersion version = myVersionsTable.getSelectedObject();
      notifyVersionSelectionChanged.accept(version != null ? version.toString() : null);
    });
    JScrollPane scrollPane = createScrollPane(myVersionsTable);
    add(scrollPane, BorderLayout.CENTER);
  }

  void setVersions(@NotNull List<GradleVersion> versions) {
    clear();
    myVersionsTable.getListTableModel().setItems(versions);
    if (!versions.isEmpty()) {
      myVersionsTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  void setEmptyText(@NotNull String text) {
    myVersionsTable.getEmptyText().setText(text);
  }

  void clear() {
    myVersionsTable.getListTableModel().setItems(Collections.emptyList());
  }
}
