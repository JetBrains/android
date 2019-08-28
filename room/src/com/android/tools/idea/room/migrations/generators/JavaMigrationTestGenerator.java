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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a test file for a given database Migration class.
 */
public class JavaMigrationTestGenerator {
  private static final String MIGRATION_TEST_NAME_TEMPLATE = "%sMigrationTest";
  private static final String TEST_DATABASE_NAME_TEMPLATE = "\"test-%s\"";
  private static final String TEST_DATABASE_FIELD_NAME_TEMPLATE = "TEST_%s";
  private static final String MIGRATION_TEST_HELPER_FIELD_NAME = "migrationTestHelper";
  private static final String MIGRATION_TEST_HELPER_TYPE = "androidx.room.testing.MigrationTestHelper";
  private static final String RULE_ANNOTATION = "org.junit.Rule";
  private static final String TEST_ANNOTATION = "org.junit.Test";
  private static final String RUN_WITH_ANNOTATION = "org.junit.runner.RunWith";
  private static final String OLD_ANDROID_JUNIT4_RUNNER = "androidx.test.runner.AndroidJUnit4";
  private static final String NEW_ANDROID_JUNIT4_RUNNER = "androidx.test.ext.junit.runners.AndroidJUnit4";
  private static final String HELPER_INIT_EXPRESSION_TEMPLATE = "%s = new MigrationTestHelper(\n%s,\n %s,\n %s);";
  private static final String INSTRUMENTATION_PARAMETER = "androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()";
  private static final String DATABASE_CLASS_CANONICAL_NAME_TEMPLATE = "%s.class.getCanonicalName()";
  private static final String FRAMEWORK_SQLITE_OPEN_HELPER_FACTORY_PARAMETER =
    "new androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()";
  private static final String MIGRATION_TEST_METHOD_NAME_TEMPLATE = "testMigrate%dTo%d";
  private static final String SUPPORT_SQLITE_DATABASE_FIELD_DECLARATION_STATEMENT_TEMPLATE =
    "androidx.sqlite.db.SupportSQLiteDatabase db = %s.createDatabase(%s, %d);";
  private static final String SUPPORT_SQLITE_DATABASE_FIELD_DECLARATION_COMMENT_TEMPLATE =
    "// Create database with schema version %s.";
  private static final String EXEC_SQL_STATEMENT = "db.execSQL(\"INSERT INTO table_name (column_name) VALUES (value);\");";
  private static final String EXEC_SQL_COMMENT = "// TODO: Insert data in the test database using SQL queries.";
  private static final String CLOSE_STATEMENT = "db.close();";
  private static final String CLOSE_COMMENT = "// Prepare for the next version.";
  private static final String RUN_AND_VALIDATE_MIGRATION_STATEMENT_TEMPLATE = "db = %s.runMigrationsAndValidate(%s, %d, true, new %s());";
  private static final String RUN_AND_VALIDATE_MIGRATION_COMMENT_TEMPLATE =
    "// Re-open the database with version %s and provide %s as the migration process.";
  private static final String ASSERT_STATEMENT = "org.junit.Assert.fail(\"TODO: Verify data after migration is correct\");";
  private static final String ASSERT_COMMENT =
    "// MigrationTestHelper automatically verifies the schema changes, but you need to validate that the data was migrated properly.";
  private static final String MIGRATION_TEST_METHOD_EXCEPTION = "java.io.IOException";

  private JavaPsiFacade myJavaPsiFacade;
  private PsiElementFactory myPsiElementFactory;
  private Project myProject;

  public JavaMigrationTestGenerator(@NotNull Project project) {
    myProject = project;
    myJavaPsiFacade = JavaPsiFacade.getInstance(project);
    myPsiElementFactory = myJavaPsiFacade.getElementFactory();
  }

  /**
   * Generates a test class for a database's migrations.
   *
   * @param targetDirectory                  the directory where to generate the test class
   * @param databaseClassFullyQualifiedName  the fully qualified name of the database class
   * @param migrationClassFullyQualifiedName the name of the migration to generate a test method for
   * @param migrationStartVersion            the schema version to migrate from
   * @param migrationEndVersion              the schema version to migrate to
   */
  public void createMigrationTest(@NotNull PsiDirectory targetDirectory,
                                  @NotNull String databaseClassFullyQualifiedName,
                                  @NotNull String migrationClassFullyQualifiedName,
                                  int migrationStartVersion,
                                  int migrationEndVersion) {
    String databaseName = StringUtil.getShortName(databaseClassFullyQualifiedName);
    String migrationTestName = String.format(MIGRATION_TEST_NAME_TEMPLATE, databaseName);
    PsiClass migrationTest = JavaDirectoryService.getInstance().createClass(targetDirectory, migrationTestName);

    addRunWithAnnotation(migrationTest);
    addTestDataBaseNameField(migrationTest, databaseName);
    addMigrationTestHelperField(migrationTest);
    addMigrationTestConstructor(migrationTest, databaseClassFullyQualifiedName);
    addMigrationTestMethod(migrationTest, migrationClassFullyQualifiedName, databaseName, migrationStartVersion, migrationEndVersion);

    JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(migrationTest);
    CodeStyleManager.getInstance(myProject).reformat(migrationTest);
  }

  private void addRunWithAnnotation(@NotNull PsiClass migrationTest) {
    PsiModifierList modifierList = migrationTest.getModifierList();
    assert modifierList != null;

    PsiAnnotation annotation = modifierList.addAnnotation(RUN_WITH_ANNOTATION);
    PsiClass runnerClass = myJavaPsiFacade.findClass(NEW_ANDROID_JUNIT4_RUNNER, migrationTest.getResolveScope());

    if (runnerClass == null) {
      runnerClass = myJavaPsiFacade.findClass(OLD_ANDROID_JUNIT4_RUNNER, migrationTest.getResolveScope());
    }

    String runnerClassQualifiedName = runnerClass != null ? runnerClass.getQualifiedName() : NEW_ANDROID_JUNIT4_RUNNER;
    annotation.setDeclaredAttributeValue(
      PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME,
      myPsiElementFactory.createExpressionFromText(runnerClassQualifiedName + ".class", null));
  }

  private void addTestDataBaseNameField(@NotNull PsiClass migrationTest,
                                        @NotNull String databaseName) {
    PsiType fieldType = PsiType.getTypeByName(CommonClassNames.JAVA_LANG_STRING, myProject, migrationTest.getResolveScope());
    PsiField testDatabaseNameField = myPsiElementFactory.createField(getTestDatabaseFieldName(databaseName), fieldType);

    PsiModifierList modifierList = testDatabaseNameField.getModifierList();
    assert modifierList != null;

    modifierList.setModifierProperty(PsiModifier.FINAL, true);
    modifierList.setModifierProperty(PsiModifier.STATIC, true);

    PsiExpression testDatabaseNameConstantInitializer = myPsiElementFactory.createExpressionFromText(
      getTestDatabaseName(databaseName), null);
    testDatabaseNameField.setInitializer(testDatabaseNameConstantInitializer);

    migrationTest.add(testDatabaseNameField);
  }

  private void addMigrationTestHelperField(@NotNull PsiClass migrationTest) {
    PsiType fieldType = myPsiElementFactory.createTypeByFQClassName(MIGRATION_TEST_HELPER_TYPE, migrationTest.getResolveScope());
    PsiField migrationTestHelperField = myPsiElementFactory.createField(MIGRATION_TEST_HELPER_FIELD_NAME, fieldType);

    PsiModifierList modifierList = migrationTestHelperField.getModifierList();
    assert modifierList != null;
    modifierList.addAnnotation(RULE_ANNOTATION);

    migrationTest.add(migrationTestHelperField);
  }

  private void addMigrationTestConstructor(@NotNull PsiClass migrationTest,
                                           @NotNull String databaseClassFullyQualifiedName) {
    PsiMethod testConstructor = myPsiElementFactory.createConstructor();
    assert testConstructor.getBody() != null;

    String helperInitializationText = String.format(HELPER_INIT_EXPRESSION_TEMPLATE,
                                                    MIGRATION_TEST_HELPER_FIELD_NAME,
                                                    INSTRUMENTATION_PARAMETER,
                                                    String.format(DATABASE_CLASS_CANONICAL_NAME_TEMPLATE,
                                                                  databaseClassFullyQualifiedName),
                                                    FRAMEWORK_SQLITE_OPEN_HELPER_FACTORY_PARAMETER);
    PsiStatement helperInitializationStatement = myPsiElementFactory.createStatementFromText(helperInitializationText, null);

    testConstructor.getBody().add(helperInitializationStatement);
    migrationTest.add(testConstructor);
  }

  private void addMigrationTestMethod(@NotNull PsiClass migrationTest,
                                      @NotNull String migrationClassName,
                                      @NotNull String databaseName,
                                      int startVersion,
                                      int endVersion) {
    PsiMethod migrationTestMethod = myPsiElementFactory.createMethod(getMigrationMethodName(startVersion, endVersion), PsiType.VOID);
    migrationTestMethod.getModifierList().addAnnotation(TEST_ANNOTATION);
    migrationTestMethod.getThrowsList().add(
      myPsiElementFactory.createFQClassNameReferenceElement(MIGRATION_TEST_METHOD_EXCEPTION,
                                                            migrationTest.getResolveScope()));
    PsiCodeBlock methodBody = migrationTestMethod.getBody();
    assert methodBody != null;

    String statementText = String.format(Locale.ENGLISH,
                                         SUPPORT_SQLITE_DATABASE_FIELD_DECLARATION_STATEMENT_TEMPLATE,
                                         MIGRATION_TEST_HELPER_FIELD_NAME,
                                         getTestDatabaseFieldName(databaseName),
                                         startVersion);
    String commentText = String.format(SUPPORT_SQLITE_DATABASE_FIELD_DECLARATION_COMMENT_TEMPLATE, startVersion);
    addMethodStatement(methodBody, statementText, commentText);

    addMethodStatement(methodBody, EXEC_SQL_STATEMENT, EXEC_SQL_COMMENT);
    addMethodStatement(methodBody, CLOSE_STATEMENT, CLOSE_COMMENT);

    statementText = String.format(Locale.ENGLISH,
                                  RUN_AND_VALIDATE_MIGRATION_STATEMENT_TEMPLATE,
                                  MIGRATION_TEST_HELPER_FIELD_NAME,
                                  getTestDatabaseFieldName(databaseName),
                                  endVersion,
                                  migrationClassName);
    commentText = String.format(RUN_AND_VALIDATE_MIGRATION_COMMENT_TEMPLATE, endVersion, StringUtil.getShortName(migrationClassName));
    addMethodStatement(methodBody, statementText, commentText);

    addMethodStatement(methodBody, ASSERT_STATEMENT, ASSERT_COMMENT);

    migrationTest.add(migrationTestMethod);
  }

  private void addMethodStatement(@NotNull PsiCodeBlock methodBody, @NotNull String statementText, @NotNull String statementCommentText) {
    PsiComment comment = myPsiElementFactory.createCommentFromText(statementCommentText, null);
    methodBody.add(comment);
    PsiStatement statement = myPsiElementFactory.createStatementFromText(statementText, null);
    methodBody.add(statement);
    CodeStyleManager.getInstance(myProject).reformat(methodBody);
  }

  private static String getMigrationMethodName(int starVersion, int endVersion) {
    return String.format(Locale.ENGLISH, MIGRATION_TEST_METHOD_NAME_TEMPLATE, starVersion, endVersion);
  }

  private static String getTestDatabaseFieldName(@NotNull String databaseName) {
    return String.format(TEST_DATABASE_FIELD_NAME_TEMPLATE, CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, databaseName));
  }

  private static String getTestDatabaseName(@NotNull String databaseName) {
    return String.format(TEST_DATABASE_NAME_TEMPLATE, CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, databaseName));
  }
}
