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
  private Project project;
  private PsiPackage targetPackage;
  private PsiDirectory targetDirectory;
  private DestinationFolderComboBox targetDirectoryComboBox;
  private ReferenceEditorComboWithBrowseButton targetPackageComboBox;

  public GenerateMigrationDialog(@NotNull Project project,
                                 @NotNull PsiPackage targetPackage,
                                 @NotNull PsiDirectory targetDirectory) {
    super(project, false);
    this.project = project;
    this.targetPackage = targetPackage;
    this.targetDirectory = targetDirectory;

    setTitle(DIALOG_TITLE);
    init();
  }

  @Override
  protected void doOKAction() {
    final MoveDestination moveDestination = targetDirectoryComboBox.selectDirectory(PackageWrapper.create(targetPackage), false);
    if (moveDestination != null) {
      targetDirectory = targetDirectory != null
                        ? WriteAction.compute(() -> moveDestination.getTargetDirectory(targetDirectory))
                        : null;
    }
    super.doOKAction();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(createTargetPackagePanel(), BorderLayout.NORTH);
    centerPanel.add(createTargetDirectoryPanel(), BorderLayout.SOUTH);

    return centerPanel;
  }

  private JPanel createTargetDirectoryPanel() {
    final JPanel targetDirectoryPanel = new JPanel(new BorderLayout());
    final JBLabel label = new JBLabel(RefactoringBundle.message("target.destination.folder"));
    targetDirectoryComboBox = new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        if (packageWasChanged()) {
          onPackageChange();
        }
        return targetPackage.getQualifiedName();
      }
    };

    targetDirectoryPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    targetDirectoryComboBox.setData(project, targetDirectory, targetPackageComboBox.getChildComponent());
    targetDirectoryPanel.add(targetDirectoryComboBox, BorderLayout.CENTER);
    targetDirectoryPanel.add(label, BorderLayout.NORTH);
    label.setLabelFor(targetDirectoryComboBox);

    return targetDirectoryPanel;
  }

  private JPanel createTargetPackagePanel() {
    JPanel targetPackagePanel = new JPanel(new BorderLayout());
    final JBLabel label = new JBLabel(RefactoringBundle.message("choose.destination.package"));

    targetPackageComboBox =
      new PackageNameReferenceEditorCombo(targetPackage.getQualifiedName(), project, "GenerateMigrationDialog.RecentsKey",
                                          RefactoringBundle.message("choose.destination.package"));

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
      targetDirectory = newDirectories[0];
      targetDirectoryComboBox.setData(project, targetDirectory, targetPackageComboBox.getChildComponent());
    }
  }

  @NotNull
  public PsiDirectory getTargetDirectory() {
    return targetDirectory;
  }

  @NotNull
  public PsiPackage getTargetPackage() {
    return targetPackage;
  }
}
