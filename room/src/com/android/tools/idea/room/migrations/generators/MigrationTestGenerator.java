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

import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Common interface for Migration test generators.
 */
public interface MigrationTestGenerator {
  String RULE_ANNOTATION_QUALIFIED_NAME = "org.junit.Rule";
  String TEST_ANNOTATION_QUALIFIED_NAME = "org.junit.Test";
  String RUN_WITH_ANNOTATION_QUALIFIED_NAME = "org.junit.runner.RunWith";
  String OLD_ANDROID_JUNIT4_RUNNER_QUALIFIED_NAME = "androidx.test.runner.AndroidJUnit4";
  String NEW_ANDROID_JUNIT4_RUNNER_QUALIFIED_NAME = "androidx.test.ext.junit.runners.AndroidJUnit4";
  String IO_EXCEPTION_QUALIFIED_NAME = "java.io.IOException";
  String ASSERT_QUALIFIED_NAME = "org.junit.Assert";

  String MIGRATION_TEST_HELPER_QUALIFIED_NAME = "androidx.room.testing.MigrationTestHelper";
  String FRAMEWORK_SQLITE_OPEN_FACTORY_HELPER_QUALIFIED_NAME = "androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory";
  String INSTRUMENTATION_REGISTRY_QUALIFIED_NAME_QUALIFIED_NAME = "androidx.test.platform.app.InstrumentationRegistry";
  String SUPPORT_SQLITE_DATABASE_QUALIFIED_NAME = "androidx.sqlite.db.SupportSQLiteDatabase";
  String MIGRATION_TEST_HELPER_FIELD_NAME = "migrationTestHelper";

  String EXEC_SQL_STATEMENT = "db.execSQL(\"INSERT INTO table_name (column_name) VALUES (value);\")";
  String CLOSE_STATEMENT = "db.close()";
  String ASSERT_STATEMENT = "%s.fail(\"TODO: Verify data after migration is correct\")";

  String EXEC_SQL_COMMENT = "// TODO: Insert data in the test database using SQL queries.";
  String CLOSE_COMMENT = "// Prepare for the next version.";
  String ASSERT_COMMENT =
    "// MigrationTestHelper automatically verifies the schema changes, but you need to validate that the data was migrated properly.";

  enum CodeType {JAVA_CODE, KOTLIN_CODE}

  /**
   * Generates a test class for a database's migrations.
   *
   * @param targetPackage the package where to generate the test class
   * @param targetDirectory the directory where to generate the test class
   * @param databaseClassFullyQualifiedName  the fully qualified name of the database class
   * @param migrationClassFullyQualifiedName the name of the migration to generate a test method for
   * @param migrationStartVersion the schema version to migrate from
   * @param migrationEndVersion the schema version to migrate to
   */
   void createMigrationTest(@NotNull PsiPackage targetPackage,
                            @NotNull PsiDirectory targetDirectory,
                            @NotNull String databaseClassFullyQualifiedName,
                            @NotNull String migrationClassFullyQualifiedName,
                            int migrationStartVersion,
                            int migrationEndVersion);

  /**
   * Returns the name of a new Migration test, given the start and end version of the migration
   *
   * @param migrationStartVersion the initial version of the database schema
   * @param migrationEndVersion the final version of the database schema
   */
  @NotNull
  static String getMigrationTestName(int migrationStartVersion, int migrationEndVersion) {
    return String.format(Locale.US, "Migration_%d_%d_Test", migrationStartVersion, migrationEndVersion);
  }

  /**
   * Returns the name of a test method for a new Migration, given the start and end version of the migration
   *
   * @param migrationStartVersion the initial version of the database schema
   * @param migrationEndVersion the final version of the database schema
   */
  @NotNull
  static String getMigrationTestMethodName(int migrationStartVersion, int migrationEndVersion) {
    return String.format(Locale.US, "testMigrate%dTo%d", migrationStartVersion, migrationEndVersion);
  }

  /**
   * Returns the name of the test database field from the new test class
   *
   * @param databaseName the name of the database to be migrated
   */
  static String getTestDatabaseFieldName(@NotNull String databaseName) {
    return String.format("TEST_%s", CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, databaseName));
  }

  /**
   * Returns the name of the test database to be used for testing the new migration
   *
   * @param databaseName the name of the database to be migrated
   */
  @NotNull
  static String getTestDatabaseName(@NotNull String databaseName) {
    return String.format("\"test-%s\"", CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, databaseName));
  }

  /**
   * Returns the appropriate JUnit runner for the test
   *
   * <p>As {@link androidx.test.runner.AndroidJUnit4} is deprecated, {@link androidx.test.ext.junit.runners.AndroidJUnit4} is
   * preferred and therefore used as the default value and fall back to the former if the later can't be found.</p>
   * @param javaPsiFacade the JavaPsiFacade corresponding to the project where we generate the migration test
   * @param migrationTestResolveScope the resolve scope of the migration scope
   */
  @NotNull
  static String selectRunnerClass(@NotNull JavaPsiFacade javaPsiFacade, @NotNull GlobalSearchScope migrationTestResolveScope) {
    String runnerQualifiedName;

    try {
      PsiClass runnerClass =
        MoreObjects.firstNonNull(javaPsiFacade.findClass(NEW_ANDROID_JUNIT4_RUNNER_QUALIFIED_NAME, migrationTestResolveScope),
                                 javaPsiFacade.findClass(OLD_ANDROID_JUNIT4_RUNNER_QUALIFIED_NAME, migrationTestResolveScope));
      runnerQualifiedName = runnerClass.getQualifiedName();
    } catch (Exception e) {
      runnerQualifiedName = NEW_ANDROID_JUNIT4_RUNNER_QUALIFIED_NAME;
    }
    assert runnerQualifiedName != null;

    return runnerQualifiedName;
  }

  @NotNull
  static String getInstrumentationParameter(@NotNull CodeType codeType) {
    String instrumentationRegistry = codeType == CodeType.JAVA_CODE
                                     ? INSTRUMENTATION_REGISTRY_QUALIFIED_NAME_QUALIFIED_NAME
                                     : StringUtil.getShortName(INSTRUMENTATION_REGISTRY_QUALIFIED_NAME_QUALIFIED_NAME);
    return String.format("%s.getInstrumentation()", instrumentationRegistry);
  }

  @NotNull
  static String getDatabaseCanonicalNameParameter(@NotNull CodeType codeType, @NotNull String databaseClassQualifiedName) {
    if (codeType == CodeType.JAVA_CODE) {
      return String.format("%s.class.getCanonicalName()", databaseClassQualifiedName);
    }

    return String.format("%s::class.java.canonicalName", StringUtil.getShortName(databaseClassQualifiedName));
  }

  @NotNull
  static String getFrameworkSqliteOpenFactoryHelperParameter (@NotNull CodeType codeType) {
    if (codeType == CodeType.JAVA_CODE) {
      return String.format("new %s()", FRAMEWORK_SQLITE_OPEN_FACTORY_HELPER_QUALIFIED_NAME);
    }

    return String.format("%s()", StringUtil.getShortName(FRAMEWORK_SQLITE_OPEN_FACTORY_HELPER_QUALIFIED_NAME));
  }

  @NotNull
  static String getHelperInitializationExpression(@NotNull CodeType codeType, @NotNull String databaseClassQualifiedName) {
    if (codeType == CodeType.JAVA_CODE) {
      return String.format("%s = new %s(\n%s,\n %s,\n %s);",
                           MIGRATION_TEST_HELPER_FIELD_NAME,
                           MIGRATION_TEST_HELPER_QUALIFIED_NAME,
                           getInstrumentationParameter(codeType),
                           getDatabaseCanonicalNameParameter(codeType, databaseClassQualifiedName),
                           getFrameworkSqliteOpenFactoryHelperParameter(codeType));
    }

    return String.format("val %s: MigrationTestHelper = MigrationTestHelper(\n%s,\n %s,\n %s)",
                         MIGRATION_TEST_HELPER_FIELD_NAME,
                         getInstrumentationParameter(codeType),
                         getDatabaseCanonicalNameParameter(codeType, databaseClassQualifiedName),
                         getFrameworkSqliteOpenFactoryHelperParameter(codeType));
  }

  @NotNull
  static String getCreateDatabaseStatement(@NotNull CodeType codeType, @NotNull String databaseName, int databaseVersion) {
    String createDatabaseStatementTemplate = codeType == CodeType.JAVA_CODE
                                             ? "androidx.sqlite.db.SupportSQLiteDatabase db = %s.createDatabase(%s, %d);"
                                             : "var db = %s.createDatabase(%s, %d)";

    return String.format(Locale.US,
                         createDatabaseStatementTemplate,
                         MIGRATION_TEST_HELPER_FIELD_NAME,
                         getTestDatabaseFieldName(databaseName),
                         databaseVersion);
  }

  @NotNull
  static String getCreateDatabaseComment(int databaseVersion) {
    return String.format(Locale.US, "// Create database with schema version %d.", databaseVersion);
  }

  @NotNull
  static String getExecSqlStatement(@NotNull CodeType codeType) {
    if (codeType == CodeType.JAVA_CODE) {
      return EXEC_SQL_STATEMENT + ";";
    }

    return EXEC_SQL_STATEMENT;
  }

  @NotNull
  static String getCloseStatement(@NotNull CodeType codeType) {
    if (codeType == CodeType.JAVA_CODE) {
      return CLOSE_STATEMENT + ";";
    }

    return CLOSE_STATEMENT;
  }

  @NotNull
  static String getRunAndValidateMigrationStatement(@NotNull CodeType codeType,
                                                    @NotNull String databaseName,
                                                    @NotNull String migrationQualifiedName,
                                                    int databaseVersion) {
    if (codeType == CodeType.JAVA_CODE) {
      return String.format(Locale.US,
                           "db = %s.runMigrationsAndValidate(%s, %d, true, new %s());",
                           MIGRATION_TEST_HELPER_FIELD_NAME,
                           getTestDatabaseFieldName(databaseName),
                           databaseVersion,
                           migrationQualifiedName);
    }

    return String.format(Locale.US,
                         "db = %s.runMigrationsAndValidate(%s, %d, true, %s())",
                         MIGRATION_TEST_HELPER_FIELD_NAME,
                         getTestDatabaseFieldName(databaseName),
                         databaseVersion,
                         StringUtil.getShortName(migrationQualifiedName));
  }

  @NotNull
  static String getRunAndValidateMigrationComment(@NotNull String migrationName,
                                                  int databaseVersion) {
    return String.format(Locale.US,
                         "// Re-open the database with version %d and provide %s as the migration process.",
                         databaseVersion,
                         migrationName);
  }

  @NotNull
  static String getAssertStatement(@NotNull CodeType codeType) {
    if (codeType == CodeType.JAVA_CODE) {
      return String.format(ASSERT_STATEMENT, ASSERT_QUALIFIED_NAME) + ";";
    }

    return String.format(ASSERT_STATEMENT, StringUtil.getShortName(ASSERT_QUALIFIED_NAME)) ;
  }
}
