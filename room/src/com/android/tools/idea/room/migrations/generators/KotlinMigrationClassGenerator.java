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

import static com.android.tools.idea.room.migrations.generators.MigrationClassGenerator.*;

import com.android.tools.idea.room.migrations.update.DatabaseUpdate;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.codeStyle.CodeStyleManager;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtClassBody;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtPackageDirective;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtParameterList;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.resolve.ImportPath;

public class KotlinMigrationClassGenerator implements MigrationClassGenerator<KtClassOrObject> {
  private static final String MIGRATION_FILE_NAME_TEMPLATE = "%s.kt";
  private static final String MIGRATION_CLASS_TEMPLATE = "object %s: %s(%d, %d) {}";
  private static final String MIGRATION_FUNCTION_TEMPLATE = "override fun %s() {}";
  private static final String PARAMETER_DECLARATION_TEMPLATE = "%s: %s";
  private static final String DATABASE_UPDATE_STATEMENT_TEMPLATE = "database.execSQL(\"\"\"%s\"\"\"\n.trimIndent())";

  private Project project;
  private KtPsiFactory ktPsiFactory;

  public KotlinMigrationClassGenerator(@NotNull Project project) {
    this.project = project;
    ktPsiFactory = new KtPsiFactory(project);
  }

  @Override
  public KtClassOrObject createMigrationClass(@NotNull PsiPackage targetPackage,
                                              @NotNull PsiDirectory targetDirectory,
                                              @NotNull DatabaseUpdate databaseUpdate) {
    String migrationClassName = getMigrationClassName(databaseUpdate.getPreviousVersion(),
                                                      databaseUpdate.getCurrentVersion());
    KtClassOrObject ktMigrationClass = ktPsiFactory.createObject(String.format(Locale.US,
                                                                               MIGRATION_CLASS_TEMPLATE,
                                                                               migrationClassName,
                                                                               StringUtil.getShortName(SUPER_CLASS_NAME),
                                                                               databaseUpdate.getPreviousVersion(),
                                                                               databaseUpdate.getCurrentVersion()));
    addMigrationFunction(ktMigrationClass, databaseUpdate);
    prepareKotlinFile(targetPackage, targetDirectory, ktMigrationClass);

    return ktMigrationClass;
  }

  private void prepareKotlinFile(@NotNull PsiPackage targetPackage,
                                 @NotNull PsiDirectory targetDirectory,
                                 @NotNull KtClassOrObject ktMigrationClass) {
    PsiFile ktMigrationFile =
      PsiFileFactory.getInstance(project).createFileFromText(String.format(MIGRATION_FILE_NAME_TEMPLATE, ktMigrationClass.getName()),
                                                             KotlinLanguage.INSTANCE, "");

    KtPackageDirective packageDirective = ktPsiFactory.createPackageDirectiveIfNeeded(new FqName(targetPackage.getQualifiedName()));
    if (packageDirective != null) {
      ktMigrationFile.add(packageDirective);
      ktMigrationFile.add(ktPsiFactory.createNewLine());
    }

    addIncludeDirective(ktMigrationFile, new FqName(SUPER_CLASS_NAME));
    addIncludeDirective(ktMigrationFile, new FqName(MIGRATION_METHOD_PARAMETER_TYPE));

    ktMigrationFile.add(ktPsiFactory.createNewLine());
    ktMigrationFile.add(ktMigrationClass);

    PsiElement newKtMigrationFile = targetDirectory.add(ktMigrationFile);
    assert newKtMigrationFile instanceof PsiFile;

    CodeStyleManager.getInstance(project).reformat(newKtMigrationFile);
    ((PsiFile)newKtMigrationFile).navigate(true);
  }

  private void addIncludeDirective(@NotNull PsiFile ktMigrationFile, @NotNull FqName qualifiedName) {
    KtImportDirective importDirective = ktPsiFactory.createImportDirective(new ImportPath(qualifiedName, false, null));
    ktMigrationFile.add(importDirective);
    ktMigrationFile.add(ktPsiFactory.createNewLine());
  }

  private void addMigrationFunction(@NotNull KtClassOrObject ktMigrationClass, @NotNull DatabaseUpdate databaseUpdate) {
    KtNamedFunction ktMigrationFunction = ktPsiFactory.createFunction(String.format(MIGRATION_FUNCTION_TEMPLATE, MIGRATION_METHOD_NAME));
    KtParameterList parameterList = ktMigrationFunction.getValueParameterList();
    assert parameterList != null;

    KtParameter ktMigrationMethodParameter = ktPsiFactory.createParameter(
      String.format(PARAMETER_DECLARATION_TEMPLATE, MIGRATION_METHOD_PARAMETER_NAME,
                    StringUtil.getShortName(MIGRATION_METHOD_PARAMETER_TYPE)));
    parameterList.addParameter(ktMigrationMethodParameter);

    List<String> sqlUpdateStatements = SqlStatementsGenerator.getMigrationStatements(databaseUpdate);
    for (String sqlStatement : sqlUpdateStatements) {
      addMigrationStatement(ktMigrationFunction, sqlStatement);
    }
    KtClassBody ktMigrationClassBody = ktMigrationClass.getBody();
    assert ktMigrationClassBody != null;

    ktMigrationClassBody.addBefore(ktMigrationFunction, ktMigrationClassBody.getRBrace());
  }

  private void addMigrationStatement(@NotNull KtNamedFunction ktMigrationMethod, @NotNull String sqlStatement) {
    KtExpression expression =
      ktPsiFactory.createExpression(String.format(DATABASE_UPDATE_STATEMENT_TEMPLATE, prepareSqlStatement(sqlStatement)));
    KtBlockExpression methodBodyExpression = ktMigrationMethod.getBodyBlockExpression();
    assert methodBodyExpression != null;

    methodBodyExpression.addBefore(expression, methodBodyExpression.getRBrace());
    methodBodyExpression.addBefore(ktPsiFactory.createNewLine(), methodBodyExpression.getRBrace());
  }

  private static String prepareSqlStatement(String sqlStatement) {
    // Trims statement and escapes "$" in order to generate valid Kotlin code
    return trimSqlStatement(sqlStatement).replace("$", "${\"$\"}");
  }
}
