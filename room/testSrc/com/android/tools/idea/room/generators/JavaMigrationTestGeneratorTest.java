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

import static com.google.common.truth.Truth.*;

import com.android.tools.idea.room.migrations.generators.JavaMigrationTestGenerator;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.android.AndroidTestCase;

public class JavaMigrationTestGeneratorTest extends AndroidTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package androidx.room.migration;" +
                       "public abstract class Migration {}");

    myFixture.addClass("package androidx.sqlite.db;" +
                       "public abstract class SupportSQLiteDatabase {" +
                       "public void execSQL(String statement) {}" +
                       "public void close() {}" +
                       "}");

    myFixture.addClass("package org.junit.runner;" +
                       "public @interface RunWith {" +
                       "java.lang.Class<androidx.test.ext.junit.runners.AndroidJUnit4> value();" +
                       "}");

    myFixture.addClass("package androidx.test.ext.junit.runners;" +
                       "public class AndroidJUnit4 {}");
    myFixture.addClass("public class Instrumentation {}");

    myFixture.addClass("package androidx.room.testing;" +
                       "public class MigrationTestHelper {" +
                       "public MigrationTestHelper(" +
                       "Instrumentation instrumentation, " +
                       "String assetsFolder, " +
                       "androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory factory) {}" +
                       "public androidx.sqlite.db.SupportSQLiteDatabase createDatabase(String name, int version) {}" +
                       "public androidx.sqlite.db.SupportSQLiteDatabase runMigrationsAndValidate(" +
                       "String name, int version, boolean validateDroppedTables, androidx.room.migration.Migration... migrations) {}" +
                       "}");

    myFixture.addClass("package androidx.test.platform.app;" +
                       "public final class InstrumentationRegistry {" +
                       "public static Instrumentation getInstrumentation() {}" +
                       "}");

    myFixture.addClass("package androidx.sqlite.db.framework;" +
                       "public final class FrameworkSQLiteOpenHelperFactory {}");

    myFixture.addClass("package org.junit;" +
                       "public @interface Rule {}");

    myFixture.addClass("package org.junit;" +
                       "public @interface Test {}");

    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "public static void fail(String text) {}" +
                       "}");

    myFixture.addClass("package com.example;" +
                       "class Migration_1_2 extends androidx.room.migration.Migration {}");
  }

  public void testMigrationTestGenerator() {
    PsiClass databaseClass = myFixture.addClass("package com.example;" +
                                                "public class AppDatabase {}");
    PsiDirectory targetDirectory = databaseClass.getContainingFile().getParent();

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      JavaMigrationTestGenerator javaMigrationTestGenerator = new JavaMigrationTestGenerator(myFixture.getProject());
      javaMigrationTestGenerator.createMigrationTest(targetDirectory,
                                                     "com.example.AppDatabase",
                                                     "com.example.Migration_1_2",
                                                     1,
                                                     2);
    });

    PsiClass migrationTest = myFixture.findClass("com.example.AppDatabaseMigrationTest");

    assertThat(migrationTest).isNotNull();
    myFixture.openFileInEditor(migrationTest.getContainingFile().getVirtualFile());
    myFixture.checkHighlighting();
    assertEquals("package com.example;\n" +
                 "\n" +
                 "import androidx.room.testing.MigrationTestHelper;\n" +
                 "import androidx.sqlite.db.SupportSQLiteDatabase;\n" +
                 "import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;\n" +
                 "import androidx.test.ext.junit.runners.AndroidJUnit4;\n" +
                 "import androidx.test.platform.app.InstrumentationRegistry;\n" +
                 "\n" +
                 "import org.junit.Assert;\n" +
                 "import org.junit.Rule;\n" +
                 "import org.junit.Test;\n" +
                 "import org.junit.runner.RunWith;\n" +
                 "\n" +
                 "import java.io.IOException;\n" +
                 "\n" +
                 "@RunWith(AndroidJUnit4.class)\n" +
                 "class AppDatabaseMigrationTest {\n" +
                 "    private static final String TEST_APP_DATABASE = \"test-app-database\";\n" +
                 "    @Rule\n" +
                 "    private MigrationTestHelper migrationTestHelper;\n" +
                 "\n" +
                 "    public AppDatabaseMigrationTest() {\n" +
                 "        migrationTestHelper = new MigrationTestHelper(\n" +
                 "                InstrumentationRegistry.getInstrumentation(),\n" +
                 "                AppDatabase.class.getCanonicalName(),\n" +
                 "                new FrameworkSQLiteOpenHelperFactory());\n" +
                 "    }\n" +
                 "\n" +
                 "    @Test\n" +
                 "    public void testMigrate1To2() throws IOException {\n" +
                 "        // Create database with schema version 1.\n" +
                 "        SupportSQLiteDatabase db = migrationTestHelper.createDatabase(TEST_APP_DATABASE, 1);\n" +
                 "        // TODO: Insert data in the test database using SQL queries.\n" +
                 "        db.execSQL(\"INSERT INTO table_name (column_name) VALUES (value);\");\n" +
                 "        // Prepare for the next version.\n" +
                 "        db.close();\n" +
                 "        // Re-open the database with version 2 and provide Migration_1_2 as the migration process.\n" +
                 "        db = migrationTestHelper.runMigrationsAndValidate(TEST_APP_DATABASE, 2, true, new Migration_1_2());\n" +
                 "        // MigrationTestHelper automatically verifies the schema changes, but you need to validate that the data was migrated properly.\n" +
                 "        Assert.fail(\"TODO: Verify data after migration is correct\");\n" +
                 "    }\n" +
                 "}\n",
                 migrationTest.getContainingFile().getText());
  }
}
