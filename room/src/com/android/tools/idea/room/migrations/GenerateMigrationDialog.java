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
package com.android.tools.idea.room.migrations;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenerateMigrationDialog extends DialogWrapper {
  private static final String DIALOG_TITLE = "Generate a Room Migration";
  private static final String TARGET_PACKAGE_LABEL = "Choose target package package";
  private static final String MIGRATION_CLASS_COMBO_BOX_LABEL = "Choose destination directory for the migration class";
  private static final String MIGRATION_TEST_COMBO_BOX_LABEL = "Choose destination directory for the migration test";
  private Project project;
  private PsiPackage targetPackage;
  private PsiDirectory migrationClassDirectory;
  private PsiDirectory migrationTestDirectory;
  private DestinationFolderComboBox migrationClassDirectoryComboBox;
  private DestinationFolderComboBox migrationTestDirectoryComboBox;
  private ReferenceEditorComboWithBrowseButton targetPackageComboBox;

  public GenerateMigrationDialog(@NotNull Project project,
                                 @NotNull PsiPackage targetPackage,
                                 @NotNull PsiDirectory migrationClassDirectory,
                                 @NotNull PsiDirectory migrationTestDirectory) {
    super(project, false);
    this.project = project;
    this.targetPackage = targetPackage;
    this.migrationClassDirectory = migrationClassDirectory;
    this.migrationTestDirectory = migrationTestDirectory;

    targetPackageComboBox = new PackageNameReferenceEditorCombo(targetPackage.getQualifiedName(), project, "", TARGET_PACKAGE_LABEL);

    migrationClassDirectoryComboBox = createDestinationFolderComboBox();
    migrationClassDirectoryComboBox.setData(project, migrationClassDirectory, targetPackageComboBox.getChildComponent());

    migrationTestDirectoryComboBox = createDestinationFolderComboBox();
    migrationTestDirectoryComboBox.setData(project, migrationTestDirectory, targetPackageComboBox.getChildComponent());

    setTitle(DIALOG_TITLE);
    init();
  }

  @Override
  protected void doOKAction() {
    final MoveDestination classMoveDestination =
      migrationClassDirectoryComboBox.selectDirectory(PackageWrapper.create(targetPackage), false);
    if (classMoveDestination != null) {
      migrationClassDirectory = migrationClassDirectory != null
                                ? WriteAction.compute(() -> classMoveDestination.getTargetDirectory(migrationClassDirectory))
                                : null;
    }

    final MoveDestination testMoveDestination =
      migrationTestDirectoryComboBox.selectDirectory(PackageWrapper.create(targetPackage), false);
    if (testMoveDestination != null) {
      migrationTestDirectory = migrationTestDirectory != null
                               ? WriteAction.compute(() -> testMoveDestination.getTargetDirectory(migrationTestDirectory))
                               : null;
    }
    super.doOKAction();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(createTargetPackagePanel(), BorderLayout.NORTH);

    JPanel directoriesPanel = new JPanel((new BorderLayout()));
    directoriesPanel.add(createTargetDirectoryPanel(migrationClassDirectoryComboBox, MIGRATION_CLASS_COMBO_BOX_LABEL), BorderLayout.NORTH);
    directoriesPanel.add(createTargetDirectoryPanel(migrationTestDirectoryComboBox, MIGRATION_TEST_COMBO_BOX_LABEL), BorderLayout.SOUTH);

    centerPanel.add(directoriesPanel, BorderLayout.SOUTH);

    return centerPanel;
  }

  private DestinationFolderComboBox createDestinationFolderComboBox() {
    return new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        if (packageWasChanged()) {
          onPackageChange();
        }
        return targetPackage.getQualifiedName();
      }
    };
  }

  private JPanel createTargetDirectoryPanel(@NotNull DestinationFolderComboBox comboBox,
                                            @NotNull String labelText) {
    final JPanel targetDirectoryPanel = new JPanel(new BorderLayout());
    final JBLabel label = new JBLabel(labelText);

    targetDirectoryPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    targetDirectoryPanel.add(comboBox, BorderLayout.CENTER);
    targetDirectoryPanel.add(label, BorderLayout.NORTH);
    label.setLabelFor(comboBox);

    return targetDirectoryPanel;
  }

  private JPanel createTargetPackagePanel() {
    JPanel targetPackagePanel = new JPanel(new BorderLayout());
    final JBLabel label = new JBLabel(RefactoringBundle.message("choose.destination.package"));

    targetPackagePanel.add(targetPackageComboBox, BorderLayout.CENTER);
    targetPackagePanel.add(label, BorderLayout.NORTH);
    label.setLabelFor(targetPackageComboBox);
    return targetPackagePanel;
  }

  private boolean packageWasChanged() {
    return !targetPackage.getQualifiedName().equals(targetPackageComboBox.getText().trim());
  }

  private void onPackageChange() {
    PsiPackage newPackage = JavaPsiFacade.getInstance(project).findPackage(targetPackageComboBox.getText().trim());
    if (newPackage == null) {
      return;
    }

    PsiDirectory[] newDirectories = newPackage.getDirectories(GlobalSearchScope.projectScope(project));
    if (newDirectories.length > 0) {
      targetPackage = newPackage;
      migrationClassDirectory = newDirectories[0];
      migrationClassDirectoryComboBox.setData(project, migrationClassDirectory, targetPackageComboBox.getChildComponent());
    }
  }

  @NotNull
  public PsiDirectory getMigrationClassDirectory() {
    return migrationClassDirectory;
  }

  @NotNull
  public PsiDirectory getMigrationTestDirectory() {
    return migrationTestDirectory;
  }

  @NotNull
  public PsiPackage getTargetPackage() {
    return targetPackage;
  }
}
