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
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public class JavaMigrationClassGenerator {
  private static final String SUPER_CLASS_NAME = "androidx.room.migration.Migration";
  private static final String MIGRATION_CLASS_NAME_TEMPLATE = "Migration_%d_%d";
  private static final String MIGRATION_METHOD_NAME = "migrate";
  private static final String MIGRATION_METHOD_PARAMETER_NAME = "database";
  private static final String MIGRATION_METHOD_PARAMETER_TYPE = "androidx.sqlite.db.SupportSQLiteDatabase";
  private static final String MIGRATION_METHOD_PARAMETER_ANNOTATION = "androidx.annotation.NonNull";
  private static final String MIGRATION_METHOD_RETURN_TYPE = "void";
  private static final String MIGRATION_METHOD_ANNOTATION = "Override";
  private static final String DATABASE_UPDATE_STATEMENT_TEMPLATE = "database.execSQL(\"%s\");";
  private static final String CONSTRUCTOR_PARAMETER1 = "startVersion";
  private static final String CONSTRUCTOR_PARAMETER2 = "endVersion";
  private static final String CONSTRUCTOR_PARAMETERS_TYPE = "int";
  private static final String SUPER_CONSTRUCTOR_CALL = "super(startVersion, endVersion);";

  /**
   * Generates a Migration class which produces the update from a database schema to another
   *
   * @param project         the project
   * @param targetDirectory the directory where to generate the class
   * @param databaseUpdate  the DatabaseUpdate object which describes the updates to be performed
   */
  public static void createMigrationClass(@NotNull Project project,
                                          @NotNull PsiDirectory targetDirectory,
                                          @NotNull DatabaseUpdate databaseUpdate) {
    String migrationClassName = String.format(Locale.US,
                                              MIGRATION_CLASS_NAME_TEMPLATE,
                                              databaseUpdate.getPreviousVersion(),
                                              databaseUpdate.getCurrentVersion());
    PsiClass migrationClass = JavaDirectoryService.getInstance().createClass(targetDirectory, migrationClassName);
    addSuperClass(migrationClass, project);
    addMigrationConstructor(migrationClass, project);
    addMigrationMethod(migrationClass, project, databaseUpdate);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(migrationClass);
    CodeStyleManager.getInstance(project).reformat(migrationClass);

    migrationClass.navigate(true);
  }

  private static void addSuperClass(@NotNull PsiClass migrationClass, @NotNull Project project) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiReferenceList extendsList = migrationClass.getExtendsList();

    if (extendsList == null) {
      return;
    }

    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiJavaCodeReferenceElement superClassRef;
    superClassRef = elementFactory.createFQClassNameReferenceElement(SUPER_CLASS_NAME, scope);

    extendsList.add(superClassRef);
  }

  private static void addMigrationMethod(@NotNull PsiClass migrationClass,
                                         @NotNull Project project,
                                         @NotNull DatabaseUpdate databaseUpdate) {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiType returnType = elementFactory.createPrimitiveType(MIGRATION_METHOD_RETURN_TYPE);
    PsiMethod migrationMethod = elementFactory.createMethod(MIGRATION_METHOD_NAME, returnType);

    migrationMethod.getModifierList().addAnnotation(MIGRATION_METHOD_ANNOTATION);

    PsiType parameterType = elementFactory.createTypeByFQClassName(MIGRATION_METHOD_PARAMETER_TYPE, GlobalSearchScope.allScope(project));
    PsiParameter parameter = elementFactory.createParameter(MIGRATION_METHOD_PARAMETER_NAME, parameterType);

    if (parameter.getModifierList() != null &&
        JavaPsiFacade.getInstance(project).findClass(MIGRATION_METHOD_PARAMETER_ANNOTATION, GlobalSearchScope.allScope(project)) != null) {
      parameter.getModifierList().addAnnotation(MIGRATION_METHOD_PARAMETER_ANNOTATION);
    }
    migrationMethod.getParameterList().add(parameter);

    List<String> sqlUpdateStatements = SqlStatementsGenerator.getUpdateStatements(databaseUpdate);
    for (String sqlStatement : sqlUpdateStatements) {
      addMigrationStatement(migrationMethod, project, sqlStatement);
    }

    migrationClass.add(migrationMethod);
  }

  private static void addMigrationStatement(@NotNull PsiMethod migrationMethod,
                                            @NotNull Project project,
                                            @NotNull String sqlStatement) {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiStatement migrationStatement =
      elementFactory.createStatementFromText(trimStatement(String.format(DATABASE_UPDATE_STATEMENT_TEMPLATE, sqlStatement)), null);

    PsiCodeBlock methodBody = migrationMethod.getBody();
    if (methodBody == null) {
      return;
    }

    methodBody.addAfter(migrationStatement, methodBody.getLastBodyElement());
  }

  private static void addMigrationConstructor(@NotNull PsiClass migrationClass,
                                              @NotNull Project project) {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiMethod migrationConstructor = elementFactory.createConstructor();
    PsiType parametersType = elementFactory.createPrimitiveType(CONSTRUCTOR_PARAMETERS_TYPE);
    if (parametersType == null) {
      return;
    }

    PsiParameter startVersionParameter = elementFactory.createParameter(CONSTRUCTOR_PARAMETER1, parametersType);
    PsiParameter endVersionParameter = elementFactory.createParameter(CONSTRUCTOR_PARAMETER2, parametersType);

    migrationConstructor.getParameterList().add(startVersionParameter);
    migrationConstructor.getParameterList().add(endVersionParameter);

    if (migrationConstructor.getBody() == null) {
      return;
    }
    migrationConstructor.getBody().add(elementFactory.createStatementFromText(SUPER_CONSTRUCTOR_CALL, null));

    migrationClass.add(migrationConstructor);
  }

  private static String trimStatement(String statement) {
    return statement.replace("\n", " ").replace("\t", "");
  }
}
