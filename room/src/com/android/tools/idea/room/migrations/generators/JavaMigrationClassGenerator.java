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
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
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
  private static final String MIGRATION_METHOD_ANNOTATION = "java.lang.Override";
  private static final String DATABASE_UPDATE_STATEMENT_TEMPLATE = "database.execSQL(\"%s\");";
  private static final String SUPER_CONSTRUCTOR_CALL_TEMPLATE = "super(%d, %d);";

  private JavaPsiFacade myJavaPsiFacade;
  private PsiElementFactory myPsiElementFactory;
  private Project myProject;

  public JavaMigrationClassGenerator(@NotNull Project project) {
    myProject = project;
    myJavaPsiFacade = JavaPsiFacade.getInstance(project);
    myPsiElementFactory = myJavaPsiFacade.getElementFactory();
  }

  /**
   * Generates a Migration class which produces the update from a database schema to another
   *
   * @param targetDirectory the directory where to generate the class
   * @param databaseUpdate  the DatabaseUpdate object which describes the updates to be performed
   */
  public void createMigrationClass(@NotNull PsiDirectory targetDirectory,
                                   @NotNull PsiPackage targetPackage,
                                   @NotNull DatabaseUpdate databaseUpdate) {
    String migrationClassName = String.format(Locale.US,
                                              MIGRATION_CLASS_NAME_TEMPLATE,
                                              databaseUpdate.getPreviousVersion(),
                                              databaseUpdate.getCurrentVersion());
    PsiClass migrationClass = JavaDirectoryService.getInstance().createClass(targetDirectory, migrationClassName);
    addPackage(migrationClass, targetPackage);
    addSuperClass(migrationClass);
    addMigrationConstructor(migrationClass, databaseUpdate);
    addMigrationMethod(migrationClass, databaseUpdate);
    JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(migrationClass);
    CodeStyleManager.getInstance(myProject).reformat(migrationClass);

    migrationClass.navigate(true);
  }

  private void addPackage(@NotNull PsiClass migrationClass, @NotNull PsiPackage targetPackage) {
    ((PsiJavaFile)migrationClass.getContainingFile()).setPackageName(targetPackage.getQualifiedName());
  }

  private void addSuperClass(@NotNull PsiClass migrationClass) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    final PsiReferenceList extendsList = migrationClass.getExtendsList();

    if (extendsList == null) {
      return;
    }

    PsiJavaCodeReferenceElement superClassRef;
    superClassRef = myPsiElementFactory.createFQClassNameReferenceElement(SUPER_CLASS_NAME, scope);

    extendsList.add(superClassRef);
  }

  private void addMigrationMethod(@NotNull PsiClass migrationClass,
                                  @NotNull DatabaseUpdate databaseUpdate) {
    PsiMethod migrationMethod = myPsiElementFactory.createMethod(MIGRATION_METHOD_NAME, PsiType.VOID);

    migrationMethod.getModifierList().addAnnotation(MIGRATION_METHOD_ANNOTATION);

    PsiType parameterType = myPsiElementFactory.createTypeByFQClassName(MIGRATION_METHOD_PARAMETER_TYPE, migrationClass.getResolveScope());
    PsiParameter parameter = myPsiElementFactory.createParameter(MIGRATION_METHOD_PARAMETER_NAME, parameterType);

    if (parameter.getModifierList() != null &&
        myJavaPsiFacade.findClass(MIGRATION_METHOD_PARAMETER_ANNOTATION, migrationClass.getResolveScope()) != null) {
      parameter.getModifierList().addAnnotation(MIGRATION_METHOD_PARAMETER_ANNOTATION);
    }
    migrationMethod.getParameterList().add(parameter);

    List<String> sqlUpdateStatements = SqlStatementsGenerator.getUpdateStatements(databaseUpdate);
    for (String sqlStatement : sqlUpdateStatements) {
      addMigrationStatement(migrationMethod, sqlStatement);
    }

    migrationClass.add(migrationMethod);
  }

  private void addMigrationStatement(@NotNull PsiMethod migrationMethod,
                                     @NotNull String sqlStatement) {
    PsiStatement migrationStatement =
      myPsiElementFactory.createStatementFromText(trimStatement(String.format(DATABASE_UPDATE_STATEMENT_TEMPLATE, sqlStatement)), null);

    PsiCodeBlock methodBody = migrationMethod.getBody();
    if (methodBody == null) {
      return;
    }

    methodBody.addAfter(migrationStatement, methodBody.getLastBodyElement());
  }

  private void addMigrationConstructor(@NotNull PsiClass migrationClass,
                                       @NotNull DatabaseUpdate databaseUpdate) {
    PsiMethod migrationConstructor = myPsiElementFactory.createConstructor();

    if (migrationConstructor.getBody() == null) {
      return;
    }
    migrationConstructor.getBody().add(myPsiElementFactory.createStatementFromText(
      String.format(Locale.US, SUPER_CONSTRUCTOR_CALL_TEMPLATE, databaseUpdate.getPreviousVersion(), databaseUpdate.getCurrentVersion()),
      null));

    migrationClass.add(migrationConstructor);
  }

  private String trimStatement(String statement) {
    return statement.replace("(\n", "(").replace("\n)", ")").replace("\n", " ").replace("\t", "");
  }
}
