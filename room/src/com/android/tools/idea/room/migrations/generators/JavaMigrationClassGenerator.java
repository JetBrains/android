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
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class JavaMigrationClassGenerator {
  private static final String SUPER_CLASS_NAME = "androidx.room.migration.Migration";
  private static final String MIGRATION_CLASS_NAME_TEMPLATE = "Migration_%d_%d";
  private static final String MIGRATION_METHOD_TEMPLATE = "@Override\n" +
                                                          "public void migrate(androidx.sqlite.db.SupportSQLiteDatabase database) {\n%s}\n";
  private static final String DATABASE_UPDATE_STATEMENT_TEMPLATE = "\tdatabase.execSQL(\"%s\");";

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
    List<String> sqlUpdateStatements = SqlStatementsGenerator.getUpdateStatements(databaseUpdate);
    String methodText = String.format(MIGRATION_METHOD_TEMPLATE,
                                      sqlUpdateStatements.stream()
                                        .map(statement -> String.format(DATABASE_UPDATE_STATEMENT_TEMPLATE, trimStatement(statement)))
                                        .collect(Collectors.joining("\n")));
    PsiMethod migrationMethod = elementFactory.createMethodFromText(methodText, null);

    migrationClass.add(migrationMethod);
  }

  private static String trimStatement(String statement) {
    return statement.replace("\n", " ").replace("\t", "");
  }
}
