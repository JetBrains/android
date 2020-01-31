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

import com.android.tools.idea.room.migrations.update.EntityUpdate;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ui.components.JBPanel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Step of the {@link GenerateMigrationWizard} for collecting user input needed in order to decide which columns have been renamed.
 */
public class GenerateMigrationWizardRenameColumnsStep implements GenerateMigrationWizardStep {
  private static final String RENAME_COLUMNS_STEP_LABEL_TEMPLATE =
    "Specify which of the following columns from table %s has been renamed and what is the new name for each of them";

  private boolean shouldBeSkipped;
  private GenerateMigrationWizardData wizardData;
  private Map<String, GenerateMigrationWizard.RenamePanel> tableToRenameColumnsPanelMapping;
  private Map<String, GenerateMigrationWizard.RenamePanel> renamedTableToRenameColumnsPanelMapping;

  private JBPanel rootPanel;
  private JBPanel dynamicPanel;

  public GenerateMigrationWizardRenameColumnsStep(@NotNull GenerateMigrationWizardData wizardData) {
    this.wizardData = wizardData;
    this.tableToRenameColumnsPanelMapping = new HashMap<>();
    this.rootPanel = new JBPanel();
    this.rootPanel.setLayout(new BoxLayout(this.rootPanel, BoxLayout.Y_AXIS));

    for (EntityUpdate entityUpdate : wizardData.getDatabaseUpdate().getModifiedEntities().values()) {
      if (entityUpdate.getDeletedFields().isEmpty()) {
        continue;
      }
      String tableName = entityUpdate.getNewTableName();
      List<String> oldColumnNames = new ArrayList<>(entityUpdate.getDeletedFields().keySet());
      List<String> newColumnNames = new ArrayList<>(entityUpdate.getNewFields().keySet());
      GenerateMigrationWizard.RenamePanel renameColumnsPanel =
        new GenerateMigrationWizard.RenamePanel(String.format(RENAME_COLUMNS_STEP_LABEL_TEMPLATE, tableName),
                                                oldColumnNames,
                                                newColumnNames);
      tableToRenameColumnsPanelMapping.put(tableName, renameColumnsPanel);
      rootPanel.add(renameColumnsPanel.getRenameStepPanel());
    }

    shouldBeSkipped = tableToRenameColumnsPanelMapping.isEmpty();

    this.dynamicPanel = new JBPanel();
    this.dynamicPanel.setLayout(new BoxLayout(this.dynamicPanel, BoxLayout.Y_AXIS));

    rootPanel.add(dynamicPanel);
  }

  @Override
  public void _init() {
    dynamicPanel.removeAll();
    renamedTableToRenameColumnsPanelMapping = new HashMap<>();
    for (EntityUpdate entityUpdate : wizardData.getUserIdentifiedEntityUpdates()) {
      String tableName = entityUpdate.getNewTableName();
      List<String> oldColumnNames = new ArrayList<>(entityUpdate.getDeletedFields().keySet());
      List<String> newColumnNames = new ArrayList<>(entityUpdate.getNewFields().keySet());
      GenerateMigrationWizard.RenamePanel renameColumnsPanel =
        new GenerateMigrationWizard.RenamePanel(String.format(RENAME_COLUMNS_STEP_LABEL_TEMPLATE, tableName),
                                                oldColumnNames,
                                                newColumnNames);
      renamedTableToRenameColumnsPanelMapping.put(tableName, renameColumnsPanel);
      dynamicPanel.add(renameColumnsPanel.getRenameStepPanel());
    }

    shouldBeSkipped |= renamedTableToRenameColumnsPanelMapping.isEmpty();
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    Map<String, Map<String, String>> tableToRenamedColumnsMapping = new HashMap<>();

    tableToRenameColumnsPanelMapping.forEach(
      (tableName, renameColumnsPanel) -> tableToRenamedColumnsMapping.put(tableName, renameColumnsPanel.getOldToNewNamesMapping()));
    renamedTableToRenameColumnsPanelMapping.forEach(
      (tableName, renameColumnsPanel) -> tableToRenamedColumnsMapping.put(tableName, renameColumnsPanel.getOldToNewNamesMapping()));

    wizardData.updateTableToRenamedColumnsMapping(tableToRenamedColumnsMapping);
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public JComponent getComponent() {
    return rootPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Override
  public boolean shouldBeSkipped() {
    return shouldBeSkipped;
  }
}
