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
package com.android.tools.idea.room.generators;

import static com.android.tools.idea.room.generators.TestUtils.createDatabaseBundle;
import static com.android.tools.idea.room.generators.TestUtils.createEntityBundle;
import static com.android.tools.idea.room.generators.TestUtils.createFieldBundle;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.room.migrations.generators.KotlinMigrationClassGenerator;
import com.android.tools.idea.room.migrations.json.DatabaseBundle;
import com.android.tools.idea.room.migrations.json.EntityBundle;
import com.android.tools.idea.room.migrations.json.FieldBundle;
import com.android.tools.idea.room.migrations.update.DatabaseUpdate;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jetbrains.android.AndroidTestCase;

public class KotlinMigrationClassGeneratorTest extends AndroidTestCase {
  private DatabaseBundle db1;
  private DatabaseBundle db2;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package androidx.room.migration;" +
                       "public abstract class Migration {" +
                       "public Migration(int starVersion, int endVersion) {}" +
                       "public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {}" +
                       "}");

    myFixture.addClass("package androidx.sqlite.db;" +
                       "public abstract class SupportSQLiteDatabase {" +
                       "public void execSQL(String sql) {}}");

    FieldBundle field1 = createFieldBundle("column1", "TEXT", null);
    FieldBundle field2 = createFieldBundle("column2", "TEXT", null);
    FieldBundle field3 = createFieldBundle("column3", "TEXT", null);

    EntityBundle entity1 = createEntityBundle("table1", field1, field2);
    EntityBundle entity2 = createEntityBundle("table2", field1, field2, field3);
    EntityBundle entity3 = createEntityBundle("table3", field1, field3);

    db1 = createDatabaseBundle(1, entity1, entity2);
    db2 = createDatabaseBundle(2, entity1, entity2, entity3);
  }

  public void testMigrationClassCreation() {
    PsiClass databaseClass = myFixture.addClass("package com.example;" +
                                                "public class AppDatabase {}");
    PsiPackage targetPackage = myFixture.findPackage("com.example");
    PsiDirectory targetDirectory = databaseClass.getContainingFile().getParent();

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      KotlinMigrationClassGenerator ktMigrationClassGenerator = new KotlinMigrationClassGenerator(myFixture.getProject());
      ktMigrationClassGenerator.createMigrationClass(targetPackage, targetDirectory, new DatabaseUpdate(db1, db2));
    });

    PsiClass migrationClass = myFixture.findClass("com.example.Migration_1_2");

    assertThat(migrationClass).isNotNull();
    assertEquals("package com.example\n" +
                 "\n" +
                 "import androidx.room.migration.Migration\n" +
                 "import androidx.sqlite.db.SupportSQLiteDatabase\n" +
                 "\n" +
                 "object Migration_1_2 : Migration(1, 2) {\n" +
                 "    override fun migrate(database: SupportSQLiteDatabase) {\n" +
                 "        database.execSQL(\n            \"\"\"CREATE TABLE table3 (column1 TEXT, column3 TEXT, PRIMARY KEY (column1));\"\"\"\n" +
                 "                .trimIndent()\n        )\n" +
                 "    }\n" +
                 "}",
                 migrationClass.getContainingFile().getText());
  }
}