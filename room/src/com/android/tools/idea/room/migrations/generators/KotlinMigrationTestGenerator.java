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

import static com.android.tools.idea.room.migrations.generators.MigrationTestGenerator.*;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtClassBody;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFunction;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtPackageDirective;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.resolve.ImportPath;

/**
 * Creates a new Migration test in Kotlin.
 *
 * <p>The class will always be generated in a new file</p>
 */
public class KotlinMigrationTestGenerator implements MigrationTestGenerator {
  private static final String MIGRATION_FILE_NAME_TEMPLATE = "%s.kt";
  private static final String MIGRATION_TEST_CLASS_TEMPLATE = "class %s {}";
  private static final String MIGRATION_TEST_FUNCTION_TEMPLATE = " fun %s() {}";

  private Project project;
  private KtPsiFactory ktPsiFactory;

  public KotlinMigrationTestGenerator(@NotNull Project project) {
    this.project = project;
    ktPsiFactory = new KtPsiFactory(project);
  }

  @Override
  public void createMigrationTest(@NotNull PsiPackage targetPackage,
                                  @NotNull PsiDirectory targetDirectory,
                                  @NotNull String databaseClassFullyQualifiedName,
                                  @NotNull String migrationClassFullyQualifiedName,
                                  int migrationStartVersion,
                                  int migrationEndVersion) {
    String migrationClassName = getMigrationTestName(migrationStartVersion, migrationEndVersion);

    KtClass ktMigrationTest = ktPsiFactory.createClass(String.format(MIGRATION_TEST_CLASS_TEMPLATE, migrationClassName));

    KtAnnotationEntry runnerAnnotation = ktPsiFactory.createAnnotationEntry(
      getAnnotationWithParameter(RUN_WITH_ANNOTATION_QUALIFIED_NAME,
                                 selectRunnerClass(JavaPsiFacade.getInstance(project), ktMigrationTest.getResolveScope())));
    ktMigrationTest.addAnnotationEntry(runnerAnnotation);

    addTestDatabaseNameField(ktMigrationTest, StringUtil.getShortName(databaseClassFullyQualifiedName));
    addMigrationTestHelperField(ktMigrationTest, databaseClassFullyQualifiedName);
    addMigrationTestFunction(ktMigrationTest,
                             StringUtil.getShortName(databaseClassFullyQualifiedName),
                             migrationClassFullyQualifiedName,
                             migrationStartVersion,
                             migrationEndVersion);
    prepareKotlinFile(targetPackage, targetDirectory, ktMigrationTest);
  }

  private void prepareKotlinFile(@NotNull PsiPackage targetPackage,
                                 @NotNull PsiDirectory targetDirectory,
                                 @NotNull KtClass ktMigrationTest) {
    PsiFile ktMigrationTestFile =
      PsiFileFactory.getInstance(project).createFileFromText(String.format(MIGRATION_FILE_NAME_TEMPLATE, ktMigrationTest.getName()),
                                                             KotlinLanguage.INSTANCE, "");

    KtPackageDirective packageDirective = ktPsiFactory.createPackageDirectiveIfNeeded(new FqName(targetPackage.getQualifiedName()));
    if (packageDirective != null) {
      ktMigrationTestFile.add(packageDirective);
      ktMigrationTestFile.add(ktPsiFactory.createNewLine());
    }

    addIncludeDirective(ktMigrationTestFile, new FqName(MIGRATION_TEST_HELPER_QUALIFIED_NAME));
    addIncludeDirective(ktMigrationTestFile, new FqName(FRAMEWORK_SQLITE_OPEN_FACTORY_HELPER_QUALIFIED_NAME));
    addIncludeDirective(ktMigrationTestFile, new FqName(INSTRUMENTATION_REGISTRY_QUALIFIED_NAME_QUALIFIED_NAME));
    addIncludeDirective(ktMigrationTestFile, new FqName(selectRunnerClass(JavaPsiFacade.getInstance(project),
                                                                          ktMigrationTestFile.getResolveScope())));
    addIncludeDirective(ktMigrationTestFile, new FqName(IO_EXCEPTION_QUALIFIED_NAME));
    addIncludeDirective(ktMigrationTestFile, new FqName(ASSERT_QUALIFIED_NAME));
    addIncludeDirective(ktMigrationTestFile, new FqName(RUN_WITH_ANNOTATION_QUALIFIED_NAME));
    addIncludeDirective(ktMigrationTestFile, new FqName(RULE_ANNOTATION_QUALIFIED_NAME));
    addIncludeDirective(ktMigrationTestFile, new FqName(TEST_ANNOTATION_QUALIFIED_NAME));



    ktMigrationTestFile.add(ktPsiFactory.createNewLine());
    ktMigrationTestFile.add(ktMigrationTest);

    PsiElement element = targetDirectory.add(ktMigrationTestFile);
    CodeStyleManager.getInstance(project).reformat(element);
    if (element instanceof PsiFile) {
      ((PsiFile)element).navigate(true);
    }
  }

  private void addIncludeDirective(@NotNull PsiFile ktMigrationFile, @NotNull FqName qualifiedName) {
    KtImportDirective importDirective = ktPsiFactory.createImportDirective(new ImportPath(qualifiedName, false, null));
    ktMigrationFile.add(importDirective);
    ktMigrationFile.add(ktPsiFactory.createNewLine());
  }

  private void addTestDatabaseNameField(@NotNull KtClass ktMigrationTest, @NotNull String databaseName) {
    KtDeclaration testDatabaseNameFieldDeclaration =
      ktPsiFactory.createDeclaration(String.format("private val %s = %s",
                                                   getTestDatabaseFieldName(databaseName),
                                                   getTestDatabaseName(databaseName)));

    addElementToTestClass(testDatabaseNameFieldDeclaration, ktMigrationTest);
    addElementToTestClass(ktPsiFactory.createNewLine(), ktMigrationTest);
    addElementToTestClass(ktPsiFactory.createNewLine(), ktMigrationTest);
  }

  private void addMigrationTestHelperField(@NotNull KtClass ktMigrationTest, @NotNull String databaseClassQualifiedName) {
    KtDeclaration migrationTestHelperFieldDeclaration =
      ktPsiFactory.createDeclaration(getHelperInitializationExpression(CodeType.KOTLIN_CODE, databaseClassQualifiedName));

    KtAnnotationEntry ruleAnnotation = ktPsiFactory.createAnnotationEntry(getAnnotationName(RULE_ANNOTATION_QUALIFIED_NAME));
    ruleAnnotation = migrationTestHelperFieldDeclaration.addAnnotationEntry(ruleAnnotation);
    ruleAnnotation.add(ktPsiFactory.createNewLine());

    addElementToTestClass(migrationTestHelperFieldDeclaration, ktMigrationTest);
    addElementToTestClass(ktPsiFactory.createNewLine(), ktMigrationTest);
    addElementToTestClass(ktPsiFactory.createNewLine(), ktMigrationTest);
  }

  private void addMigrationTestFunction(@NotNull KtClass ktMigrationTest,
                                        @NotNull String databaseName,
                                        @NotNull String migrationQualifiedName,
                                        int startVersion,
                                        int endVersion) {
    String testMethodName = getMigrationTestMethodName(startVersion, endVersion);
    KtNamedFunction ktMigrationFunction = ktPsiFactory.createFunction(String.format(MIGRATION_TEST_FUNCTION_TEMPLATE, testMethodName));

    KtAnnotationEntry testAnnotation = ktPsiFactory.createAnnotationEntry(getAnnotationName(TEST_ANNOTATION_QUALIFIED_NAME));
    KtAnnotationEntry throwsAnnotation =
      ktPsiFactory.createAnnotationEntry(getAnnotationWithParameter("Throws", IO_EXCEPTION_QUALIFIED_NAME));

    ktMigrationFunction.addAnnotationEntry(throwsAnnotation);
    ktMigrationFunction.addAnnotationEntry(testAnnotation);

    addExpressionToMethod(ktPsiFactory.createDeclaration(getCreateDatabaseStatement(CodeType.KOTLIN_CODE, databaseName, startVersion)),
                          ktPsiFactory.createComment(getCreateDatabaseComment(startVersion)),
                          ktMigrationFunction);

    addExpressionToMethod(ktPsiFactory.createExpression(getExecSqlStatement(CodeType.KOTLIN_CODE)),
                          ktPsiFactory.createComment(EXEC_SQL_COMMENT),
                          ktMigrationFunction);

    addExpressionToMethod(ktPsiFactory.createExpression(getCloseStatement(CodeType.KOTLIN_CODE)),
                          ktPsiFactory.createComment(CLOSE_COMMENT),
                          ktMigrationFunction);

    addExpressionToMethod(ktPsiFactory.createExpression(getRunAndValidateMigrationStatement(CodeType.KOTLIN_CODE,
                                                                                            databaseName,
                                                                                            migrationQualifiedName,
                                                                                            endVersion)),
                          ktPsiFactory.createComment(getRunAndValidateMigrationComment(StringUtil.getShortName(migrationQualifiedName),
                                                                                       endVersion)),
                          ktMigrationFunction);

    addExpressionToMethod(ktPsiFactory.createExpression(getAssertStatement(CodeType.KOTLIN_CODE)),
                          ktPsiFactory.createComment(ASSERT_COMMENT),
                          ktMigrationFunction);

    addElementToTestClass(ktMigrationFunction, ktMigrationTest);
  }

  private void addExpressionToMethod(@NotNull KtExpression expression,
                                     @NotNull PsiComment comment,
                                     @NotNull KtFunction ktMigrationTestFunction) {
    KtBlockExpression functionBody = ktMigrationTestFunction.getBodyBlockExpression();
    assert functionBody != null;

    functionBody.addBefore(ktPsiFactory.createNewLine(), functionBody.getRBrace());
    functionBody.addBefore(comment, functionBody.getRBrace());
    functionBody.addBefore(ktPsiFactory.createNewLine(), functionBody.getRBrace());
    functionBody.addBefore(expression, functionBody.getRBrace());
  }

  private static void addElementToTestClass(@NotNull PsiElement psiElement, @NotNull KtClass ktMigrationTest) {
    KtClassBody ktMigrationClassBody = ktMigrationTest.getBody();
    assert ktMigrationClassBody != null;

    ktMigrationClassBody.addBefore(psiElement, ktMigrationClassBody.getRBrace());
  }

  private static String getAnnotationName(@NotNull String annotationQualifiedName) {
    return String.format(("@%s"), StringUtil.getShortName(annotationQualifiedName));
  }

  private static String getAnnotationWithParameter(@NotNull String annotationQualifiedName, @NotNull String qualifiedParameterName) {
    return String.format("@%s(%s::class)",
                         StringUtil.getShortName(annotationQualifiedName),
                         StringUtil.getShortName(qualifiedParameterName));
  }
}
