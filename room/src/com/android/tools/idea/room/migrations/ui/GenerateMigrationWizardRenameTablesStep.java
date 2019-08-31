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

import static com.android.tools.idea.room.migrations.ui.GenerateMigrationWizardRenameStepElement.*;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.Step;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public class GenerateMigrationWizardRenameTablesStep implements Step {
  private static final String RENAME_TABLES_STEP_LABEL =
    "Specify which of the following tables has been renamed and what is the new name for each of them";
  private static final String HEADER_TABLE_NAME_LABEL = "Table Name";
  private static final String HEADER_OPERATION_LABEL = "Operation";
  private static final String HEADER_NEW_NAME_LABEL = "New Name";

  private GenerateMigrationWizardData wizardData;
  private List<GenerateMigrationWizardRenameStepElement> renameStepElements;
  private JBPanel renameTablesStepPanel;
  private JBScrollPane renameTablesStepScrollPane;

  public GenerateMigrationWizardRenameTablesStep(@NotNull GenerateMigrationWizardData wizardData) {
    this.wizardData = wizardData;
    renameStepElements = new ArrayList<>();
    renameTablesStepPanel = new JBPanel(new BorderLayout());
    renameTablesStepScrollPane = new JBScrollPane();

    JBLabel renameTablesStepLabel = new JBLabel(RENAME_TABLES_STEP_LABEL);
    renameTablesStepPanel.add(renameTablesStepLabel, BorderLayout.NORTH);
    renameTablesStepLabel.setLabelFor(renameTablesStepPanel);

    JBPanel tablesListPanel = new JBPanel();
    tablesListPanel.setLayout(new BoxLayout(tablesListPanel, BoxLayout.Y_AXIS));
    List<String> oldTableNames = new ArrayList<>(wizardData.getDatabaseUpdate().getDeletedEntities().keySet());
    List<String> newTableNames = new ArrayList<>(wizardData.getDatabaseUpdate().getNewEntities().keySet());

    JBPanel headerPanel = new JBPanel();
    headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
    headerPanel.setMaximumSize(new Dimension(MAX_PANEL_WIDTH, MAX_PANEL_HEIGHT));

    JBLabel tableNameLabel = new JBLabel(HEADER_TABLE_NAME_LABEL);
    tableNameLabel.setMaximumSize(new Dimension(MAX_PANEL_COMPONENT_WIDTH, MAX_PANEL_COMPONENT_HEIGHT));
    JBLabel operationLabel = new JBLabel(HEADER_OPERATION_LABEL);
    operationLabel.setMaximumSize(new Dimension(MAX_PANEL_COMPONENT_WIDTH, MAX_PANEL_COMPONENT_HEIGHT));
    JBLabel newNameLabel = new JBLabel(HEADER_NEW_NAME_LABEL);
    newNameLabel.setMaximumSize(new Dimension(MAX_PANEL_COMPONENT_WIDTH, MAX_PANEL_COMPONENT_HEIGHT));

    headerPanel.add(tableNameLabel);
    headerPanel.add(operationLabel);
    headerPanel.add(newNameLabel);

    tablesListPanel.add(headerPanel);

    for (String oldTableName : oldTableNames) {
      GenerateMigrationWizardRenameStepElement renameStepElement =
        new GenerateMigrationWizardRenameStepElement(oldTableName, newTableNames);
      renameStepElements.add(renameStepElement);
      tablesListPanel.add(renameStepElement.getRenameStepElementPanel());
    }
    renameTablesStepScrollPane.getViewport().setView(tablesListPanel);

    renameTablesStepPanel.add(renameTablesStepScrollPane, BorderLayout.CENTER);
  }

  @Override
  public void _init() {}

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    Map<String, String> oldToNewTableNamesMapping = new HashMap<>();
    for (GenerateMigrationWizardRenameStepElement renameStepElement : renameStepElements) {
      if (renameStepElement.markedAsRenamed()) {
        oldToNewTableNamesMapping.put(renameStepElement.getInitialName(), renameStepElement.getNewName());
      }
    }

    wizardData.updateRenamedTables(oldToNewTableNamesMapping);
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public JComponent getComponent() {
    return renameTablesStepPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return renameTablesStepScrollPane;
  }
}
