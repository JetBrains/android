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
package com.android.tools.idea.room.migrations.generators;

import com.android.tools.idea.room.migrations.update.DatabaseUpdate;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Common interface for Migration generators.
 */
public interface MigrationClassGenerator<T extends PsiElement> {
  String SUPER_CLASS_NAME = "androidx.room.migration.Migration";
  String MIGRATION_METHOD_NAME = "migrate";
  String MIGRATION_METHOD_PARAMETER_NAME = "database";
  String MIGRATION_METHOD_PARAMETER_TYPE = "androidx.sqlite.db.SupportSQLiteDatabase";

  /**
   * Generates a Migration class which produces the update from a database schema to another
   *
   * @param targetPackage the directory where to generate the class
   * @param targetDirectory the directory where to generate the class
   * @param databaseUpdate  the DatabaseUpdate object which describes the updates to be performed
   */
  T createMigrationClass(@NotNull PsiPackage targetPackage,
                         @NotNull PsiDirectory targetDirectory,
                         @NotNull DatabaseUpdate databaseUpdate);

  /**
   * Returns the name of a new Migration class, given the start and end version pf the migration
   *
   * @param migrationStartVersion the initial version of the database schema
   * @param migrationEndVersion the final version of the database schema
   */
  static String getMigrationClassName(int migrationStartVersion, int migrationEndVersion) {
    return String.format(Locale.US, "Migration_%d_%d", migrationStartVersion, migrationEndVersion);
  }

  /**
   * Trims tabs and newlines from sql queries.
   *
   * <p>This is needed because currently the migration generators do not handle multi-line strings.</p>
   */
  static String trimSqlStatement(String sqlStatement) {
    return sqlStatement.replace("(\n", "(").replace("\n)", ")").replace("\n", " ").replace("\t", "");
  }
}
