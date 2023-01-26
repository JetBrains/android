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
import static com.android.tools.idea.room.migrations.generators.MigrationClassGenerator.getMigrationClassName;
import static com.android.tools.idea.room.migrations.generators.MigrationClassGenerator.trimSqlStatement;

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
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a new Migration class in Java, given the description of the database schema changes.
 *
 * <p>The class will always be generated in a new file</p>
 */
public class JavaMigrationClassGenerator implements MigrationClassGenerator<PsiClass> {
  private static final String NOT_NULL_ANNOTATION = "androidx.annotation.NonNull";
  private static final String OVERRIDE_ANNOTATION = "java.lang.Override";
  private static final String SUPER_CONSTRUCTOR_CALL_TEMPLATE = "super(%d, %d);";
  private static final String DATABASE_UPDATE_STATEMENT_TEMPLATE = "database.execSQL(\"%s\");";


  private JavaPsiFacade myJavaPsiFacade;
  private PsiElementFactory myPsiElementFactory;
  private Project myProject;

  public JavaMigrationClassGenerator(@NotNull Project project) {
    myProject = project;
    myJavaPsiFacade = JavaPsiFacade.getInstance(project);
    myPsiElementFactory = myJavaPsiFacade.getElementFactory();
  }

  @Override
  @NotNull
  public PsiClass createMigrationClass(@NotNull PsiPackage targetPackage,
                                       @NotNull PsiDirectory targetDirectory,
                                       @NotNull DatabaseUpdate databaseUpdate) {
    String migrationClassName = getMigrationClassName(databaseUpdate.getPreviousVersion(), databaseUpdate.getCurrentVersion());
    PsiClass migrationClass = JavaDirectoryService.getInstance().createClass(targetDirectory, migrationClassName);

    makePublic(migrationClass);
    addSuperClass(migrationClass);
    addMigrationConstructor(migrationClass, databaseUpdate);
    addMigrationMethod(migrationClass, databaseUpdate);
    JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(migrationClass);
    CodeStyleManager.getInstance(myProject).reformat(migrationClass);

    migrationClass.navigate(true);

    return migrationClass;
  }

  private void addSuperClass(@NotNull PsiClass migrationClass) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    PsiReferenceList extendsList = migrationClass.getExtendsList();
    assert extendsList != null;

    PsiJavaCodeReferenceElement superClassRef;
    superClassRef = myPsiElementFactory.createFQClassNameReferenceElement(SUPER_CLASS_NAME, scope);

    extendsList.add(superClassRef);
  }

  private void addMigrationMethod(@NotNull PsiClass migrationClass,
                                  @NotNull DatabaseUpdate databaseUpdate) {
    PsiMethod migrationMethod = myPsiElementFactory.createMethod(MIGRATION_METHOD_NAME, PsiTypes.voidType());

    migrationMethod.getModifierList().addAnnotation(OVERRIDE_ANNOTATION);

    PsiType parameterType = myPsiElementFactory.createTypeByFQClassName(MIGRATION_METHOD_PARAMETER_TYPE, migrationClass.getResolveScope());
    PsiParameter parameter = myPsiElementFactory.createParameter(MIGRATION_METHOD_PARAMETER_NAME, parameterType);
    assert parameter.getModifierList() != null;

    if (myJavaPsiFacade.findClass(NOT_NULL_ANNOTATION, migrationClass.getResolveScope()) != null) {
      parameter.getModifierList().addAnnotation(NOT_NULL_ANNOTATION);
    }
    migrationMethod.getParameterList().add(parameter);

    List<String> sqlUpdateStatements = SqlStatementsGenerator.getMigrationStatements(databaseUpdate);
    for (String sqlStatement : sqlUpdateStatements) {
      addMigrationStatement(migrationMethod, sqlStatement);
    }

    migrationClass.add(migrationMethod);
  }

  private void addMigrationStatement(@NotNull PsiMethod migrationMethod,
                                     @NotNull String sqlStatement) {
    PsiStatement migrationStatement =
      myPsiElementFactory.createStatementFromText(trimSqlStatement(String.format(DATABASE_UPDATE_STATEMENT_TEMPLATE, sqlStatement)), null);
    PsiCodeBlock methodBody = migrationMethod.getBody();
    assert methodBody != null;

    methodBody.addAfter(migrationStatement, methodBody.getLastBodyElement());
  }

  private void addMigrationConstructor(@NotNull PsiClass migrationClass,
                                       @NotNull DatabaseUpdate databaseUpdate) {
    PsiMethod migrationConstructor = myPsiElementFactory.createConstructor();
    assert migrationConstructor.getBody() != null;

    migrationConstructor.getBody().add(myPsiElementFactory.createStatementFromText(
      String.format(Locale.US, SUPER_CONSTRUCTOR_CALL_TEMPLATE, databaseUpdate.getPreviousVersion(), databaseUpdate.getCurrentVersion()),
      null));

    migrationClass.add(migrationConstructor);
  }
}
