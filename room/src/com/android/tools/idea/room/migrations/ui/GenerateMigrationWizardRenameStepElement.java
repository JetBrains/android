/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.room.migrations.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import javax.swing.BoxLayout;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

public class GenerateMigrationWizardRenameStepElement {
  public static final int MAX_PANEL_WIDTH = 900;
  public static final int MAX_PANEL_HEIGHT = 35;
  public static final int MAX_PANEL_COMPONENT_WIDTH = 300;
  public static final int MAX_PANEL_COMPONENT_HEIGHT = 30;

  private static final String DELETED = "deleted";
  private static final String RENAMED = "renamed";
  private static final String[] STATUSES = {DELETED, RENAMED};

  private JBLabel initialName;
  private ComboBox<String> statusComboBox;
  private ComboBox<String> newNamesComboBox;
  private JBPanel renameStepElementPanel;

  public GenerateMigrationWizardRenameStepElement(@NotNull String initialName, @NotNull List<String> newNames) {
    this.initialName = new JBLabel(initialName);
    this.initialName.setMaximumSize(new Dimension(MAX_PANEL_COMPONENT_WIDTH, MAX_PANEL_COMPONENT_HEIGHT));

    this.statusComboBox = new ComboBox<>(STATUSES);
    this.statusComboBox.setMaximumSize(new Dimension(MAX_PANEL_COMPONENT_WIDTH, MAX_PANEL_COMPONENT_HEIGHT));

    this.newNamesComboBox = new ComboBox<>();
    newNames.forEach(newName -> newNamesComboBox.addItem(newName));
    this.newNamesComboBox.setMaximumSize(new Dimension(MAX_PANEL_COMPONENT_WIDTH, MAX_PANEL_COMPONENT_HEIGHT));
    this.newNamesComboBox.setEnabled(false);

    renameStepElementPanel = new JBPanel<>();
    renameStepElementPanel.setLayout(new BoxLayout(renameStepElementPanel, BoxLayout.X_AXIS));
    renameStepElementPanel.add(this.initialName);
    renameStepElementPanel.add(this.statusComboBox);
    renameStepElementPanel.add(this.newNamesComboBox);
    renameStepElementPanel.setMaximumSize(new Dimension(MAX_PANEL_WIDTH, MAX_PANEL_HEIGHT));

    statusComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getItem().equals(RENAMED)) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            newNamesComboBox.setEnabled(true);
          } else {
            newNamesComboBox.setEnabled(false);
          }
        }
      }
    });
  }

  @NotNull
  public String getInitialName() {
    return initialName.getText();
  }

  @NotNull String getStatus() {
    return statusComboBox.getItemAt(statusComboBox.getSelectedIndex());
  }

  @NotNull String getNewName() {
    return newNamesComboBox.getItemAt(newNamesComboBox.getSelectedIndex());
  }

  @NotNull
  public JBPanel getRenameStepElementPanel() {
    return renameStepElementPanel;
  }

  public boolean markedAsRenamed() {
    return getStatus().equals(RENAMED);
  }
}
