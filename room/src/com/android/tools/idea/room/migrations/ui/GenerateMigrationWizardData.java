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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;

public class GenerateMigrationWizardData {
  private Project project;
  private PsiPackage targetPackage;
  private PsiDirectory migrationClassDirectory;
  private PsiDirectory migrationTestDirectory;

  GenerateMigrationWizardData(@NotNull Project project,
                              @NotNull PsiPackage targetPackage,
                              @NotNull PsiDirectory migrationClassDirectory,
                              @NotNull PsiDirectory migrationTestDirectory) {
    this.project = project;
    this.targetPackage = targetPackage;
    this.migrationClassDirectory = migrationClassDirectory;
    this.migrationTestDirectory = migrationTestDirectory;
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
}
