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

import static com.android.tools.idea.room.migrations.ui.GenerateMigrationWizard.RenamePanelElement.*;

import com.android.tools.idea.room.migrations.update.DatabaseUpdate;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Custom wizard for the generate migration feature.
 *
 * <p>Provides steps for selecting the destination folders for the new Migration class and test and collecting user input needed in order
 * to decide which tables have been renamed.</p>
 */
public class GenerateMigrationWizard extends AbstractWizard<GenerateMigrationWizardStep> {
  private static final String WIZARD_TITLE = "Generate a Room Migration";

  private GenerateMigrationWizardData myWizardData;
  private GenerateMigrationWizardStep selectDestinationStep;
  private GenerateMigrationWizardStep renameTablesStep;
  private GenerateMigrationWizardStep renameColumnsStep;

  public GenerateMigrationWizard(@NotNull Project project,
                                 @NotNull PsiPackage targetPackage,
                                 @NotNull PsiDirectory migrationClassDirectory,
                                 @NotNull PsiDirectory migrationTestDirectory,
                                 @NotNull DatabaseUpdate databaseUpdate) {
    super(WIZARD_TITLE,project);
    myWizardData = new GenerateMigrationWizardData(project, targetPackage, migrationClassDirectory, migrationTestDirectory, databaseUpdate);
    selectDestinationStep = new GenerateMigrationWizardSelectDestinationStep(myWizardData);
    renameTablesStep = new GenerateMigrationWizardRenameTablesStep(myWizardData);
    renameColumnsStep = new GenerateMigrationWizardRenameColumnsStep(myWizardData);

    addStep(selectDestinationStep);
    if (!renameTablesStep.shouldBeSkipped()) {
      addStep(renameTablesStep);
    }
    if (!renameColumnsStep.shouldBeSkipped()) {
      addStep(renameColumnsStep);
    }

    init();
  }

  @Override
  protected boolean isLastStep() {
    return super.isLastStep() || allNextStepsCanBeSkipped(getCurrentStep());
  }

  @Override
  protected int getNextStep(int step) {
    if (step == getStepCount() - 1 || allNextStepsCanBeSkipped(step)) {
      return step;
    }

    int nextStep = step + 1;
    while (nextStep < getStepCount() - 1 && mySteps.get(nextStep).shouldBeSkipped()) {
      nextStep++;
    }

    return nextStep;
  }

  @Override
  protected int getPreviousStep(int step) {
    if (step == 0) {
      return step;
    }

    int previousStep = step - 1;
    while (previousStep > 0 && mySteps.get(previousStep).shouldBeSkipped()) {
      previousStep --;
    }

    return previousStep;
  }

  @Nullable
  @Override
  protected String getHelpID() {
    return null;
  }

  @NotNull
  public PsiPackage getTargetPackage() {
    return myWizardData.getTargetPackage();
  }

  @NotNull
  public PsiDirectory getMigrationClassDirectory() {
    return myWizardData.getMigrationClassDirectory();
  }

  @NotNull
  public PsiDirectory getMigrationTestDirectory() {
    return myWizardData.getMigrationTestDirectory();
  }

  @NotNull
  public DatabaseUpdate getUserReviewedDatabaseUpdate() {
    return myWizardData.getUserReviewedDatabaseUpdate();
  }

  private boolean allNextStepsCanBeSkipped(int step) {
    for (int futureStep = step + 1; futureStep < getStepCount(); futureStep++) {
      if (!mySteps.get(futureStep).shouldBeSkipped()) {
        return false;
      }
    }

    return false;
  }

  /**
   * Builds a panel for renaming either a table or a column.
   *
   * The panel provides a label with the old name of the element to be renamed, a combo box for specifying whether the element was deleted or
   * renamed and another combo box for specifying the new name of the element, if applicable.
   */
  public static class RenamePanelElement {
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

    public RenamePanelElement(@NotNull String initialName, @NotNull List<String> newNames) {
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

  /**
   * Builds a panel for renaming either all tables from a database or all columns of a table.
   *
   * It provides a {@link RenamePanelElement} for each table or column which could be renamed.
   */
  public static class RenamePanel {
    private static final String HEADER_NAME_LABEL = "Old Name";
    private static final String HEADER_OPERATION_LABEL = "Operation";
    private static final String HEADER_NEW_NAME_LABEL = "New Name";

    private List<RenamePanelElement> renameStepElements;
    private JBPanel renameStepPanel;

    public RenamePanel(@NotNull String label,
                       @NotNull List<String> oldNames,
                       @NotNull List<String> newNames) {
      renameStepElements = new ArrayList<>();
      renameStepPanel = new JBPanel(new BorderLayout());

      JBLabel renameStepPanelLabel = new JBLabel(label);
      renameStepPanel.add(renameStepPanelLabel, BorderLayout.NORTH);
      renameStepPanelLabel.setLabelFor(renameStepPanel);

      JBPanel listPanel = new JBPanel();
      listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

      JBPanel headerPanel = new JBPanel();
      headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
      headerPanel.setMaximumSize(new Dimension(MAX_PANEL_WIDTH, MAX_PANEL_HEIGHT));

      JBLabel tableNameLabel = new JBLabel(HEADER_NAME_LABEL);
      tableNameLabel.setMaximumSize(new Dimension(MAX_PANEL_COMPONENT_WIDTH, MAX_PANEL_COMPONENT_HEIGHT));
      JBLabel operationLabel = new JBLabel(HEADER_OPERATION_LABEL);
      operationLabel.setMaximumSize(new Dimension(MAX_PANEL_COMPONENT_WIDTH, MAX_PANEL_COMPONENT_HEIGHT));
      JBLabel newNameLabel = new JBLabel(HEADER_NEW_NAME_LABEL);
      newNameLabel.setMaximumSize(new Dimension(MAX_PANEL_COMPONENT_WIDTH, MAX_PANEL_COMPONENT_HEIGHT));

      headerPanel.add(tableNameLabel);
      headerPanel.add(operationLabel);
      headerPanel.add(newNameLabel);

      listPanel.add(headerPanel);

      for (String oldName : oldNames) {
        RenamePanelElement renameStepElement =
          new RenamePanelElement(oldName, newNames);
        renameStepElements.add(renameStepElement);
        listPanel.add(renameStepElement.getRenameStepElementPanel());
      }

      JBScrollPane renameStepScrollPane = new JBScrollPane();
      renameStepScrollPane.getViewport().setView(listPanel);

      renameStepPanel.add(renameStepScrollPane, BorderLayout.CENTER);
    }

    @NotNull
    public JBPanel getRenameStepPanel() {
      return renameStepPanel;
    }

    @NotNull
    public Map<String, String> getOldToNewNamesMapping() {
      Map<String, String> oldToNewNamesMapping = new HashMap<>();
      for (RenamePanelElement renameStepElement : renameStepElements) {
        if (renameStepElement.markedAsRenamed()) {
          oldToNewNamesMapping.put(renameStepElement.getInitialName(), renameStepElement.getNewName());
        }
      }

      return oldToNewNamesMapping;
    }
  }
}
