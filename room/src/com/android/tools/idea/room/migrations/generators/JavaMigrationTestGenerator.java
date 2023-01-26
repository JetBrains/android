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

import static com.android.tools.idea.room.migrations.generators.GeneratorsUtil.makePublic;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getAssertStatement;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getCloseStatement;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getCreateDatabaseComment;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getCreateDatabaseStatement;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getExecSqlStatement;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getHelperInitializationExpression;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getMigrationTestMethodName;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getMigrationTestName;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getRunAndValidateMigrationComment;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getRunAndValidateMigrationStatement;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getTestDatabaseFieldName;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.getTestDatabaseName;
import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.selectRunnerClass;

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
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a new Migration test in Java.
 *
 * <p>The class will always be generated in a new file</p>
 */
public class JavaMigrationTestGenerator implements MigrationTestGenerator {
  private JavaPsiFacade myJavaPsiFacade;
  private PsiElementFactory myPsiElementFactory;
  private Project myProject;

  public JavaMigrationTestGenerator(@NotNull Project project) {
    myProject = project;
    myJavaPsiFacade = JavaPsiFacade.getInstance(project);
    myPsiElementFactory = myJavaPsiFacade.getElementFactory();
  }

  @Override
  public void createMigrationTest(@NotNull PsiPackage targetPackage,
                                  @NotNull PsiDirectory targetDirectory,
                                  @NotNull String databaseClassFullyQualifiedName,
                                  @NotNull String migrationClassFullyQualifiedName,
                                  int migrationStartVersion,
                                  int migrationEndVersion) {
    String databaseName = StringUtil.getShortName(databaseClassFullyQualifiedName);
    String migrationTestName = getMigrationTestName(migrationStartVersion, migrationEndVersion);
    PsiClass migrationTest = JavaDirectoryService.getInstance().createClass(targetDirectory, migrationTestName);

    makePublic(migrationTest);
    addRunWithAnnotation(migrationTest);
    addTestDatabaseNameField(migrationTest, databaseName);
    addMigrationTestHelperField(migrationTest);
    addMigrationTestConstructor(migrationTest, databaseClassFullyQualifiedName);
    addMigrationTestMethod(migrationTest, migrationClassFullyQualifiedName, databaseName, migrationStartVersion, migrationEndVersion);

    JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(migrationTest);
    CodeStyleManager.getInstance(myProject).reformat(migrationTest);
  }

  private void addRunWithAnnotation(@NotNull PsiClass migrationTest) {
    PsiModifierList modifierList = migrationTest.getModifierList();
    assert modifierList != null;

    PsiAnnotation annotation = modifierList.addAnnotation(RUN_WITH_ANNOTATION_QUALIFIED_NAME);
    String runnerClassQualifiedName = selectRunnerClass(myJavaPsiFacade, migrationTest.getResolveScope());
    annotation.setDeclaredAttributeValue(
      PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME,
      myPsiElementFactory.createExpressionFromText(runnerClassQualifiedName + ".class", null));
  }

  private void addTestDatabaseNameField(@NotNull PsiClass migrationTest,
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
    PsiType fieldType = myPsiElementFactory.createTypeByFQClassName(MIGRATION_TEST_HELPER_QUALIFIED_NAME, migrationTest.getResolveScope());
    PsiField migrationTestHelperField = myPsiElementFactory.createField(MIGRATION_TEST_HELPER_FIELD_NAME, fieldType);

    PsiModifierList modifierList = migrationTestHelperField.getModifierList();
    assert modifierList != null;
    modifierList.addAnnotation(RULE_ANNOTATION_QUALIFIED_NAME);

    migrationTest.add(migrationTestHelperField);
  }

  private void addMigrationTestConstructor(@NotNull PsiClass migrationTest,
                                           @NotNull String databaseClassFullyQualifiedName) {
    PsiMethod testConstructor = myPsiElementFactory.createConstructor();
    assert testConstructor.getBody() != null;

    String helperInitializationText = getHelperInitializationExpression(CodeType.JAVA_CODE, databaseClassFullyQualifiedName);
    PsiStatement helperInitializationStatement = myPsiElementFactory.createStatementFromText(helperInitializationText, null);

    testConstructor.getBody().add(helperInitializationStatement);
    migrationTest.add(testConstructor);
  }

  private void addMigrationTestMethod(@NotNull PsiClass migrationTest,
                                      @NotNull String migrationClassName,
                                      @NotNull String databaseName,
                                      int startVersion,
                                      int endVersion) {
    PsiMethod migrationTestMethod = myPsiElementFactory.createMethod(getMigrationTestMethodName(startVersion, endVersion),
                                                                     PsiTypes.voidType());
    migrationTestMethod.getModifierList().addAnnotation(TEST_ANNOTATION_QUALIFIED_NAME);
    migrationTestMethod.getThrowsList().add(
      myPsiElementFactory.createFQClassNameReferenceElement(IO_EXCEPTION_QUALIFIED_NAME,
                                                            migrationTest.getResolveScope()));
    PsiCodeBlock methodBody = migrationTestMethod.getBody();
    assert methodBody != null;

    String statementText = getCreateDatabaseStatement(CodeType.JAVA_CODE, databaseName, startVersion);
    String commentText = getCreateDatabaseComment(startVersion);
    addMethodStatement(methodBody, statementText, commentText);

    addMethodStatement(methodBody, getExecSqlStatement(CodeType.JAVA_CODE), EXEC_SQL_COMMENT);
    addMethodStatement(methodBody, getCloseStatement(CodeType.JAVA_CODE), CLOSE_COMMENT);

    statementText = getRunAndValidateMigrationStatement(CodeType.JAVA_CODE, databaseName, migrationClassName, endVersion);
    commentText = getRunAndValidateMigrationComment(StringUtil.getShortName(migrationClassName), endVersion);
    addMethodStatement(methodBody, statementText, commentText);

    addMethodStatement(methodBody, getAssertStatement(CodeType.JAVA_CODE), ASSERT_COMMENT);

    migrationTest.add(migrationTestMethod);
  }

  private void addMethodStatement(@NotNull PsiCodeBlock methodBody, @NotNull String statementText, @NotNull String statementCommentText) {
    PsiComment comment = myPsiElementFactory.createCommentFromText(statementCommentText, null);
    methodBody.add(comment);
    PsiStatement statement = myPsiElementFactory.createStatementFromText(statementText, null);
    methodBody.add(statement);
    CodeStyleManager.getInstance(myProject).reformat(methodBody);
  }
}
