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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Groups together the data shared between the migration generation wizard and its steps.
 *
 * <p>The data is updated with new information from a step each time {@link com.intellij.ide.wizard.Step#_commit(boolean)} is called on
 * that step (i.e. when the user moves to a previous or next step).</p>
 */
public class GenerateMigrationWizardData {
  private Project project;
  private PsiPackage targetPackage;
  private PsiDirectory migrationClassDirectory;
  private PsiDirectory migrationTestDirectory;

  private DatabaseUpdate databaseUpdate;
  private Map<String, String> renamedTables;

  GenerateMigrationWizardData(@NotNull Project project,
                              @NotNull PsiPackage targetPackage,
                              @NotNull PsiDirectory migrationClassDirectory,
                              @NotNull PsiDirectory migrationTestDirectory,
                              @NotNull DatabaseUpdate databaseUpdate) {
    this.project = project;
    this.targetPackage = targetPackage;
    this.migrationClassDirectory = migrationClassDirectory;
    this.migrationTestDirectory = migrationTestDirectory;

    this.databaseUpdate = databaseUpdate;
    this.renamedTables = new HashMap<>();
  }

  public void updateTargetPackage(@NotNull PsiPackage targetPackage) {
    this.targetPackage = targetPackage;
  }

  public void updateMigrationClassDirectory(@NotNull PsiDirectory migrationClassDirectory) {
    this.migrationClassDirectory = migrationClassDirectory;
  }

  public void updateMigrationTestDirectory(PsiDirectory migrationTestDirectory) {
    this.migrationTestDirectory = migrationTestDirectory;
  }

  @NotNull
  public Project getProject() {
    return project;
  }

  @NotNull
  public PsiPackage getTargetPackage() {
    return targetPackage;
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
  public DatabaseUpdate getDatabaseUpdate() {
    return databaseUpdate;
  }

  public DatabaseUpdate getUserReviewedDatabaseUpdate() {
    databaseUpdate.applyRenameMapping(renamedTables);
    return databaseUpdate;
  }

  /**
   * Updates the old table name to new table name mapping based on user input
   * @param userInput a user provided mapping between the old table names and new table names.
   */
  public void updateRenamedTables(@NotNull Map<String, String> userInput) {
    renamedTables = userInput;
  }
}
