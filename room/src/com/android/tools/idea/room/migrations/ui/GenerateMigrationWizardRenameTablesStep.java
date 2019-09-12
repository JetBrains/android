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

import com.android.tools.idea.room.migrations.json.EntityBundle;
import com.android.tools.idea.room.migrations.update.EntityUpdate;
import com.android.tools.idea.room.migrations.update.SchemaDiffUtil;
import com.intellij.ide.wizard.CommitStepException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Step of the {@link GenerateMigrationWizard} for collecting user input needed in order to decide which tables have been renamed.
 */
public class GenerateMigrationWizardRenameTablesStep implements GenerateMigrationWizardStep {
  private static final String RENAME_TABLES_STEP_LABEL =
    "Specify which of the following tables has been renamed and what is the new name for each of them";

  private boolean shouldBeSkipped;
  private GenerateMigrationWizardData wizardData;
  private GenerateMigrationWizard.RenamePanel renameTablesStepPanel;

  public GenerateMigrationWizardRenameTablesStep(@NotNull GenerateMigrationWizardData wizardData) {
    this.wizardData = wizardData;
    List<String> oldTableNames = new ArrayList<>(wizardData.getDatabaseUpdate().getDeletedEntities().keySet());
    List<String> newTableNames = new ArrayList<>(wizardData.getDatabaseUpdate().getNewEntities().keySet());
    this.renameTablesStepPanel = new GenerateMigrationWizard.RenamePanel(RENAME_TABLES_STEP_LABEL, oldTableNames, newTableNames);

    shouldBeSkipped = oldTableNames.isEmpty();
  }

  @Override
  public void _init() {}

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    Map<String, String> oldToNewTableNames = renameTablesStepPanel.getOldToNewNamesMapping();
    wizardData.updateRenamedTables(oldToNewTableNames);

    // Tables which were identified by the user to be renamed might contain themselves renamed columns.
    //
    if (!oldToNewTableNames.isEmpty()) {
      updateUserIdentifiedEntityUpdates();
    }
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public JComponent getComponent() {
    return renameTablesStepPanel.getRenameStepPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  private void updateUserIdentifiedEntityUpdates() {
    List<EntityUpdate> userIdentifiedEntityUpdates = new ArrayList<>();
    for (Map.Entry<String, String> tableNames : renameTablesStepPanel.getOldToNewNamesMapping().entrySet()) {
      EntityBundle oldEntity = wizardData.getDatabaseUpdate().getDeletedEntities().get(tableNames.getKey());
      EntityBundle newEntity = wizardData.getDatabaseUpdate().getNewEntities().get(tableNames.getValue());
      assert oldEntity != null && newEntity != null;
      if (!SchemaDiffUtil.isTableStructureTheSame(oldEntity, newEntity)) {
        EntityUpdate entityUpdate = new EntityUpdate(oldEntity, newEntity);
        if (!entityUpdate.getDeletedFields().isEmpty()) {
          userIdentifiedEntityUpdates.add(entityUpdate);
        }
      }
    }
    wizardData.updateUserIdentifiedEntityUpdates(userIdentifiedEntityUpdates);
  }

  @Override
  public boolean shouldBeSkipped() {
    return shouldBeSkipped;
  }
}
