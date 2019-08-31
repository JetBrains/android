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

import com.android.tools.idea.room.migrations.update.DatabaseUpdate;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Custom wizard for the generate migration feature.
 *
 * <p>Provides steps for selecting the destination folders for the new Migration class and test and collecting user input needed in order
 * to decide which tables have been renamed.</p>
 */
public class GenerateMigrationWizard extends AbstractWizard<Step> {
  private static final String WIZARD_TITLE = "Generate a Room Migration";

  private GenerateMigrationWizardData myWizardData;

  public GenerateMigrationWizard(@NotNull Project project,
                                 @NotNull PsiPackage targetPackage,
                                 @NotNull PsiDirectory migrationClassDirectory,
                                 @NotNull PsiDirectory migrationTestDirectory,
                                 @NotNull DatabaseUpdate databaseUpdate) {
    super(WIZARD_TITLE,project);
    myWizardData = new GenerateMigrationWizardData(project, targetPackage, migrationClassDirectory, migrationTestDirectory, databaseUpdate);
    GenerateMigrationWizardSelectDestinationStep selectDestinationStep = new GenerateMigrationWizardSelectDestinationStep(myWizardData);
    GenerateMigrationWizardRenameTablesStep renameStep = new GenerateMigrationWizardRenameTablesStep(myWizardData);

    addStep(selectDestinationStep);
    addStep(renameStep);
    init();
  }

  @Nullable
  @Override
  protected String getHelpID() {
    return null;
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
}
