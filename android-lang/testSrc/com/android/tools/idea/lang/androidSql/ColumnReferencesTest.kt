/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.lang.androidSql

import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlColumnPsiReference
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.find.FindManager
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase

class ColumnReferencesTest : LightJavaCodeInsightFixtureAdtTestCase() {

  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture)
    AndroidSqlContext.Provider.EP_NAME.getPoint().registerExtension(AndroidSqlTestContext.Provider(), testRootDisposable)
  }

  fun testDefaultColumnName() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT n<caret>ame FROM User") List<String> getNames();
        }
    """.trimIndent())

    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "name"))
  }

  fun testColumnNameInsideAlterQuery() {
    val file = myFixture.configureByText(AndroidSqlFileType.INSTANCE, "ALTER TABLE User RENAME COLUMN n<caret>ame TO newName")
    val schema = file.setTestSqlSchema {
      table {
        name = "User"
        column { name = "name" }
      }
    }

    val column = (myFixture.referenceAtCaret as AndroidSqlColumnPsiReference).resolveColumn(HashSet())
    assertThat(column).isEqualTo(schema.getTable("User").getColumn("name"))
  }

  fun testCaseInsensitive_unquoted() {
    myFixture.addRoomEntity("com.example.User", "fullName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT fulln<caret>ame FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "fullName"))
  }

  fun testCaseInsensitive_quoted() {
    myFixture.addRoomEntity("com.example.User", "fullName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT `fulln<caret>ame` FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "fullName"))
  }

  fun testNameOverride() {
    myFixture.addRoomEntity(
      "com.example.User",
      FieldDefinition("fullName", "String", columnName = "full_name"))

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT full_n<caret>ame FROM User") List<String> getNames();
        }
    """.trimIndent())

    val referenceTarget = myFixture.elementAtCaret
    assertThat(referenceTarget).isInstanceOf(PsiLiteralExpression::class.java)
    assertThat(referenceTarget.text).isEqualTo("\"full_name\"")
  }

  fun testResolve_noTable() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")
    myFixture.addRoomEntity("com.example.Book", "id" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d ") List<Integer> getIds();
        }
    """.trimIndent())

    assertThat(myFixture.referenceAtCaret.resolve()).isNull()
  }

  fun testResolve_validTable() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")
    myFixture.addRoomEntity("com.example.Book", "id" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d from user") List<Integer> getIds();
        }
    """.trimIndent())

    assertThat(myFixture.referenceAtCaret.resolve()).isEqualTo(myFixture.findField("com.example.User", "id"))
  }

  fun testResolve_invalidTable() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")
    myFixture.addRoomEntity("com.example.Book", "id" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d from madeup") List<Integer> getIds();
        }
    """.trimIndent())

    assertThat(myFixture.referenceAtCaret.resolve()).isNull()
  }

  fun testConflictingResolve_join() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")
    myFixture.addRoomEntity("com.example.Book", "id" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d from user, book") List<Integer> getIds();
        }
    """.trimIndent())

    // User is first in the FROM clause, so it will be picked for resolving "id". At compile time this should fail.
    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "id"))
  }

  //TODO: remove/update after fixing b/138198019
  fun testRename_privateField() {
    myFixture.addClass(
      """
      package com.example;

      import androidx.room.Entity;
      import androidx.room.PrimaryKey;

      @Entity
      class User {
        private int privateField;
      }
      """
    )

    val file = myFixture.addClass("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT private<caret>Field FROM User") List<Integer> getIds();
        }
    """.trimIndent()).containingFile

    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    myFixture.renameElementAtCaret("field")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT field FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "field"))
  }

  fun testRename_fromSql() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("user_id")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT user_id FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "user_id"))
  }

  fun testRename_fromSql_quoted() {
    myFixture.addRoomEntity("com.example.Order", "count" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface OrderDao {
          @Query("SELECT `c<caret>ount` FROM 'Order'") List<Integer> getCounts();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.Order", "count"))
    myFixture.renameElementAtCaret("amount")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface OrderDao {
          @Query("SELECT amount FROM 'Order'") List<Integer> getCounts();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.Order", "amount"))
  }

  fun testRename_fromJava() {
    val user = myFixture.addRoomEntity("com.example.User", "id" ofType "int", "name" ofType "String").containingFile.virtualFile

    val userDao = myFixture.addClass("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT id FROM User") List<Integer> getIds();
        }
    """.trimIndent()).containingFile.virtualFile

    myFixture.openFileInEditor(user)
    myFixture.findField("com.example.User", "id").navigate(true)
    myFixture.renameElementAtCaret("user_id")

    myFixture.openFileInEditor(userDao)

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT user_id FROM User") List<Integer> getIds();
        }
    """.trimIndent())
  }

  fun testRename_escaping() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    val newName = "order" // this is a SQL keyword.
    myFixture.renameElementAtCaret(newName)

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT `$newName` FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", newName))
  }

  fun testCodeCompletion_select() {
    myFixture.addRoomEntity("com.example.User", "firstName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT f<caret>") List<String> getNames();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT firstName") List<String> getNames();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_update() {
    myFixture.addRoomEntity("com.example.User", "firstName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("UPDATE user SET f<caret>") void update();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("UPDATE user SET firstName") void update();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_insert() {
    myFixture.addRoomEntity("com.example.User", "firstName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("INSERT INTO user(f<caret>) VALUES ('Bob')") void insertBob();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("INSERT INTO user(firstName) VALUES ('Bob')") void insertBob();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_delete() {
    myFixture.addRoomEntity("com.example.User", "firstName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM user WHERE f<caret>") void delete();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM user WHERE firstName") void delete();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_caseSensitivity() {
    myFixture.addRoomEntity("com.example.User", "firstName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT firstn<caret>") List<String> getNames();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT firstName") List<String> getNames();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_multiple() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int", "name" ofType "String")
    myFixture.addRoomEntity("com.example.Address", "city" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT <caret>") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("id", myFixture.findField("com.example.User", "id")),
        Pair("name", myFixture.findField("com.example.User", "name")),
        Pair("city", myFixture.findField("com.example.Address", "city"))
      )
  }

  fun testCodeCompletion_escaping() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int", "check" ofType "boolean")
    val checkField = myFixture.findField("com.example.User", "check")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT <caret>") List<User> getAll();
        }
    """.trimIndent())

    val lookupElements = myFixture.completeBasic()

    assertThat(lookupElements.map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("`check`", checkField), // CHECK is a SQL keyword.
        Pair("id", myFixture.findField("com.example.User", "id")))

    myFixture.lookup.currentItem = lookupElements.find { it.psiElement === checkField }
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT `check`") List<User> getAll();
        }
    """.trimIndent())
  }

  fun testUsages() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT n<caret>ame FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.elementAtCaret).find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_privateFields() {
    myFixture.addClass("""
      package com.example;

      import androidx.room.Entity;

      @Entity
      public class User { private String name; }
      """.trimIndent())

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT n<caret>ame FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.elementAtCaret).find { it.file!!.language == AndroidSqlLanguage.INSTANCE }!!).isNotNull()

    assertThat(
      myFixture.findUsages(myFixture.findField("com.example.User", "name"))
        .find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_caseInsensitive() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT Name FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(
      myFixture.findUsages(myFixture.findField("com.example.User", "name"))
        .find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_nameOverride() {
    myFixture.addRoomEntity(
      "com.example.User",
      FieldDefinition("id", "int"), FieldDefinition("fullName", "String", columnName = "full_name")
    )

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT full_name FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(
      myFixture.findUsages(myFixture.findField("com.example.User", "fullName"))
        .find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_caseInsensitive_kotlin() {
    myFixture.configureByText("User.kt",
                              """
        package com.example

        import androidx.room.ColumnInfo
        import androidx.room.Entity

        @Entity
        class User() {
          val first<caret>Name: String?
        }
    """.trimIndent())

    val element = myFixture.elementAtCaret

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT FIRSTNAME FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(
      myFixture.findUsages(element)
        .find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_nameOverride_kotlin() {
    myFixture.configureByText("User.kt",
                              """
        package com.example

        import androidx.room.ColumnInfo
        import androidx.room.Entity

        @Entity
        class User() {
          @ColumnInfo(name = "override_name") val original<caret>Name: String?
        }
    """.trimIndent())

    val element = myFixture.elementAtCaret

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT override_name FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(
      myFixture.findUsages(element)
        .find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testResolve_nameOverride_kotlin() {
    myFixture.configureByText("User.kt",
                              """
        package com.example

        import androidx.room.ColumnInfo
        import androidx.room.Entity

        @Entity
        class User() {
          @ColumnInfo(name = "override_name") val originalName: String?
          @field:ColumnInfo(name = "override_name_field") val originalName_field: String?
          @get:ColumnInfo(name = "override_name_get") var originalName_get: String?
        }
    """.trimIndent())

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT ove<caret>rride_name, override_name_field, override_name_get FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.referenceAtCaret.resolve()).isNotNull()
    myFixture.moveCaret("|override_name_field")
    assertThat(myFixture.referenceAtCaret.resolve()).isNotNull()

    myFixture.moveCaret("|override_name_get")
    // Room annotation works only for property annotation (annotationEntry.useSiteTarget == null) or for FIELD annotation
    assertThat(myFixture.referenceAtCaret.resolve()).isNull()
  }

  fun testUsages_nameOverride_escaping() {
    myFixture.addRoomEntity(
      "com.example.User",
      FieldDefinition("fullName", "String", columnName = "user's name")
    )

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT `user's name` FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(
      myFixture.findUsages(myFixture.findField("com.example.User", "fullName"))
        .find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_keyword() {
    myFixture.addRoomEntity("com.example.Item", "desc" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface ItemDao {
          @Query("SELECT `desc` FROM Item") List<String> getDescriptions();
        }
    """.trimIndent())

    assertThat(
      myFixture.findUsages(myFixture.findField("com.example.Item", "desc"))
        .find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_readAction() {
    myFixture.addRoomEntity("com.example.Item", "desc" ofType "String")
    // FindManager calls referenceSearch extensions in a pooled thread without the read lock, as opposed to the EDT that
    // myFixture.findUsages uses. Make sure our Room extensions don't throw under these conditions.
    FindManager.getInstance(project).findUsages(myFixture.findField("com.example.Item", "desc"))
  }


  fun testQualifiedColumns() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT user.n<caret>ame FROM user") List<String> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "name"))
  }

  fun testQualifiedColumns_completion() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int", "name" ofType "String")
    myFixture.addRoomEntity("com.example.Book", "bid" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT user.<caret> FROM user, book") List<String> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("id", myFixture.findField("com.example.User", "id")),
        Pair("name", myFixture.findField("com.example.User", "name")))
  }

  fun testAliases() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT alias.na<caret>me FROM user AS alias") List<String> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "name"))
  }

  fun testAliases_hiding() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT user.na<caret>me FROM user AS alias") List<String> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.referenceAtCaret.resolve()).isNull()
  }

  fun testAliases_join() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int")
    myFixture.addRoomEntity("com.example.Book", "bid" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user u JOIN book b ON u.uid = b.b<caret>id") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.Book", "bid"))
  }

  fun testAliases_join_completion() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int")
    myFixture.addRoomEntity("com.example.Book", "bid" ofType "int", "title" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user u JOIN book b ON u.uid = b.<caret>") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("bid", myFixture.findField("com.example.Book", "bid")),
        Pair("title", myFixture.findField("com.example.Book", "title")))
  }

  fun testJoin_completion() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int")
    myFixture.addRoomEntity("com.example.Book", "bid" ofType "int", "title" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user u JOIN book b ON <caret>") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("uid", myFixture.findField("com.example.User", "uid")),
        Pair("bid", myFixture.findField("com.example.Book", "bid")),
        Pair("title", myFixture.findField("com.example.Book", "title")))
  }

  fun testWithClause_newTable_completion() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT uid FROM user) SELECT <caret> FROM ids") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("uid", myFixture.findField("com.example.User", "uid")))
  }

  fun testWithClause_subquery() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT <caret> FROM user) SELECT 42") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("uid", myFixture.findField("com.example.User", "uid")))
  }

  fun testFromSubquery_allColumns() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int", "name" ofType "String")
    myFixture.addRoomEntity("com.example.Book", "bid" ofType "int", "title" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM (SELECT * FROM user, book) WHERE <caret>") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("uid", myFixture.findField("com.example.User", "uid")),
        Pair("name", myFixture.findField("com.example.User", "name")),
        Pair("bid", myFixture.findField("com.example.Book", "bid")),
        Pair("title", myFixture.findField("com.example.Book", "title")))
  }

  fun testFromSubquery_allTableColumns() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int", "name" ofType "String")
    myFixture.addRoomEntity("com.example.Book", "bid" ofType "int", "title" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM (SELECT u.* FROM user u, book) WHERE <caret>") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("uid", myFixture.findField("com.example.User", "uid")),
        Pair("name", myFixture.findField("com.example.User", "name")))
  }

  fun testFromSubquery_specificColumns() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int", "name" ofType "String")
    myFixture.addRoomEntity("com.example.Book", "bid" ofType "int", "title" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM (SELECT u.uid, book.title FROM user u, book) WHERE <caret>") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
      .containsExactly(
        Pair("uid", myFixture.findField("com.example.User", "uid")),
        Pair("title", myFixture.findField("com.example.Book", "title")))
  }

  fun testWhereSubquery_selectedTablesInOuterQueries() {
    myFixture.addRoomEntity("com.example.Aaa", "a" ofType "int")
    myFixture.addRoomEntity("com.example.Bbb", "b" ofType "int")
    myFixture.addRoomEntity("com.example.Ccc", "c" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT a FROM Aaa WHERE a IN (SELECT b FROM Bbb WHERE <caret>)") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("a", "b")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT a FROM Aaa WHERE a IN (SELECT b FROM Bbb WHERE b IN (SELECT c FROM Ccc WHERE <caret>))") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("a", "b", "c")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT a FROM Aaa WHERE a IN (SELECT b FROM Bbb WHERE b IN (SELECT c FROM Ccc WHERE <caret>))") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("a", "b", "c")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT a FROM Aaa WHERE a IN (SELECT b FROM Bbb WHERE b IN (SELECT c FROM Ccc WHERE Aaa.<caret>))") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("a")
  }

  fun testWhereSubquery_withClause() {
    myFixture.addRoomEntity("com.example.Aaa", "a" ofType "int")
    myFixture.addRoomEntity("com.example.Bbb", "b" ofType "int")
    myFixture.addRoomEntity("com.example.Ccc", "c" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH t1 AS (VALUES(1)) SELECT a FROM Aaa WHERE a IN (WITH t2 AS (VALUES(2)) SELECT b FROM <caret>)")
          List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("Aaa", "Bbb", "Ccc", "t1", "t2")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH t1 AS (VALUES(1)) SELECT a FROM Aaa WHERE a IN (WITH t2 AS (VALUES(2)) SELECT b FROM Bbb WHERE a IN <caret>)")
          List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("Aaa", "Bbb", "Ccc", "t1", "t2")
  }

  fun testValueSubquery() {
    myFixture.addRoomEntity("com.example.Aaa", "a" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT (SELECT min(a) FROM aaa), (SELECT max(a) FROM <caret>)") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("Aaa")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT (SELECT min(a) FROM aaa), (SELECT max(<caret>) FROM Aaa)") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("a")
  }

  fun testValuesSubqueryAliases() {
    myFixture.addRoomEntity("com.example.Aaa", "a" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH minmax AS (SELECT (SELECT min(a) as min_a FROM Aaa), (SELECT max(a) FROM Aaa) as max_a) SELECT * FROM minmax WHERE <caret>")
          List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("max_a")
  }

  fun testSubqueryDelete() {
    myFixture.addRoomEntity("com.example.User", "score" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM user WHERE score=(SELECT min(score) FROM <caret>)")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM user WHERE score=(SELECT min(<caret>) FROM user)")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("score")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH losing_score AS (SELECT min(score) FROM user) DELETE FROM user WHERE score IN <caret>")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("User", "losing_score")
  }

  fun testSubqueryUpdate() {
    myFixture.addRoomEntity("com.example.User", "score" ofType "int", "alive" ofType "boolean")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("UPDATE user SET alive=0 WHERE score=(SELECT min(score) FROM <caret>)")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("UPDATE user SET alive=0 WHERE score=(SELECT min(<caret>) FROM user)")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("score", "alive")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH losing_score AS (SELECT min(score) FROM user) UPDATE user SET alive=0 WHERE score IN <caret>")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("User", "losing_score")
  }

  fun testAliasRenaming() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT id AS i FROM user) SELECT <caret>i FROM ids")
          List<Integer> getIds();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("user_id")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT id AS user_id FROM user) SELECT user_id FROM ids")
          List<Integer> getIds();
        }
    """.trimIndent())
  }

  fun testWithTableRenaming_columns() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH ids(x) AS (SELECT id FROM user) SELECT <caret>x FROM ids")
          List<Integer> getIds();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("user_id")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH ids(user_id) AS (SELECT id FROM user) SELECT user_id FROM ids")
          List<Integer> getIds();
        }
    """.trimIndent())
  }

  fun testColumnSubqueryAliasResolvesWithoutExplicitSelection() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM (SELECT 17 AS alias) WHERE alias > 0") List<String> getNames();
        }
    """.trimIndent())


    myFixture.moveCaret("AS |alias")
    val aliasDefinition = myFixture.elementAtCaret
    myFixture.moveCaret("WHERE |alias")
    assertThat(myFixture.elementAtCaret).isEqualTo(aliasDefinition)
  }

  fun testColumnSubqueryAlias() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT alias FROM (SELECT 17 AS alias)") List<String> getNames();
        }
    """.trimIndent())


    myFixture.moveCaret("SELECT |alias")
    val aliasDefinition = myFixture.elementAtCaret
    myFixture.moveCaret("AS |alias")
    assertThat(myFixture.elementAtCaret).isEqualTo(aliasDefinition)
  }

  fun testResolveOnlyFirstSourceColumnsInOrderClause() {
    myFixture.addRoomEntity("com.example.TableOne", "shouldResolve" ofType "String")
    myFixture.addRoomEntity("com.example.TableTwo", "shouldNotResolve" ofType "String")

    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM TableOne UNION ALL SELECT * FROM TableTwo ORDER BY s<caret>houldNotResolve") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.referenceAtCaret.resolve()).isNull()
  }

  fun testResolveOnlyFirstSourceColumnsInSubquery() {
    myFixture.addRoomEntity("com.example.TableOne", "shouldResolve" ofType "String")
    myFixture.addRoomEntity("com.example.TableTwo", "shouldNotResolve" ofType "String")

    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT s<caret>houldNotResolve FROM (SELECT * FROM TableOne UNION ALL SELECT * FROM TableTwo") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.referenceAtCaret.resolve()).isNull()
  }

  fun testRecursiveWithClause() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH recTable AS (SELECT 1 AS level UNION ALL SELECT level + 1 FROM recTable WHERE level < 10) SELECT level FROM recTable")
           List<String> getNames();
        }
    """.trimIndent())


    myFixture.moveCaret("SELECT |level + 1")
    val element = myFixture.elementAtCaret
    myFixture.moveCaret("SELECT 1 AS |level")
    assertThat(myFixture.elementAtCaret).isEqualTo(element)
  }

  fun testRecursiveWithClauseNoInfiniteLoop() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH recTable AS (SELECT * FROM recTable WHERE <caret>) SELECT * FROM recTable")
           List<String> getNames();
        }
    """.trimIndent())

    // test will finish with StackOverFlow if there is infinite loop
    myFixture.completeBasic()
  }

  fun testRecursiveWithClauseNoInfiniteLoopMutual() {
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH t1 AS (SELECT * FROM t2), t2 AS (select * from t1 WHERE <caret>) SELECT * FROM t1")
           List<String> getColumns();
        }
    """.trimIndent())

    // test will finish with StackOverFlow if there is infinite loop
    myFixture.completeBasic()
  }

  fun testRecursiveWithClauseNoInfiniteLoopMutual2() {
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH t1 AS (SELECT * FROM t2), t2 AS (select x from t1 WHERE <caret>) SELECT * FROM t1")
           List<String> getColumns();
        }
    """.trimIndent())

    // test will finish with StackOverFlow if there is infinite loop
    myFixture.completeBasic()
  }

  fun testColumnAlias() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT name AS alias FROM User WHERE alias > 0") List<String> getNames();
        }
    """.trimIndent())

    myFixture.moveCaret("AS |alias")
    val aliasDefinition = myFixture.elementAtCaret
    myFixture.moveCaret("WHERE |alias")
    assertThat(myFixture.elementAtCaret).isEqualTo(aliasDefinition)
  }

  // Separate test for ORDER BY clause because ORDER BY clause is not at the same level in a tree as WHERE, FROM or GROUP BY clause
  fun testColumnAliasWithOrderByClause() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT name AS alias FROM User ORDER BY alias") List<String> getNames();
        }
    """.trimIndent())

    myFixture.moveCaret("SELECT name AS |alias")
    val aliasDefinition = myFixture.elementAtCaret
    myFixture.moveCaret("ORDER BY |alias")
    assertThat(myFixture.elementAtCaret).isEqualTo(aliasDefinition)
  }

  fun testNotResolveColumnIfAliasOutOfScope() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT name AS alias FROM (SELECT a<caret>lias, name FROM User)") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.referenceAtCaret.resolve()).isNull()
  }

  // Regression test for b/133004192.
  fun testNotResolveNotExistingColumnFromSubquery() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT not_e<caret>xisting_column FROM (SELECT not_existing_column FROM User)") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.referenceAtCaret.resolve()).isNull()
  }

  fun testTableAliasWithColumnAlias() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")
    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT user.name AS alias FROM User AS user WHERE alias IS NOT NULL") List<String> getNames();
        }
    """.trimIndent())


    myFixture.moveCaret("AS |alias FROM")
    val aliasDefinition = myFixture.elementAtCaret

    myFixture.moveCaret("WHERE |alias")
    assertThat(myFixture.elementAtCaret).isEqualTo(aliasDefinition)
  }

  fun testParserRecovery() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user u JOIN (SELECT something stupid WHERE doesnt parse) x WHERE u.<caret>name IS NOT NULL") List<User> getUsers();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "name"))
  }

  fun testOrderBy() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int", "fullName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT fullName FROM User ORDER BY i<caret>d") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "id"))
  }

  fun testEmbedded() {
    myFixture.addClass("""
      package com.example;

      import androidx.room.Embedded;
      import androidx.room.Entity;

      @Entity
      class Aaa {
        String a;

        @Embedded(prefix="bbb_") Bbb b;
      }
      """)
    myFixture.addClass("""
      package com.example;

      import androidx.room.Embedded;

      class Bbb {
        String b;

        @Embedded Ccc c1;
        @Embedded(prefix="ccc_") Ccc c2;
      }
      """)

    myFixture.addClass("""
      package com.example;

      class Ccc {
        String c;
      }
      """)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=JAVA
      """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface SomeDao {
          @Query("SELECT * FROM aaa WHERE <caret>") List<String> getStrings();
        }
      """.trimIndent()
    )

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("a", "bbb_b", "bbb_c", "bbb_ccc_c")
  }

  fun testFts_completion() {
    myFixture.addClass(
      """
      package com.example;

      import androidx.room.Entity;
      import androidx.room.Fts4;

      @Entity
      @Fts4
      class Mail {
        String subject;
        String body;
      }
      """
    )

    myFixture.configureFromExistingVirtualFile(
      myFixture.addClass(
        //language=JAVA
        """
      package com.example;

      import androidx.room.Dao;
      import androidx.room.Query;

      @Dao
      public interface SomeDao {
        @Query("SELECT * FROM mail WHERE <caret>") List<String> getStrings();
      }
      """.trimIndent()
      ).containingFile.virtualFile
    )
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("Mail", "subject", "body")
  }

  fun testFts_rename() {
    val mail = myFixture.addClass(
      """
      package com.example;

      import androidx.room.Entity;
      import androidx.room.Fts4;

      @Entity
      @Fts4
      class Mail {
        String subject;
        String body;
      }
      """
    )

    val dao = myFixture.addClass(
      //language=JAVA
      """
      package com.example;

      import androidx.room.Dao;
      import androidx.room.Query;

      @Dao
      public interface SomeDao {
        @Query("SELECT * FROM mail WHERE mail MATCH 'foo'") List<String> getStrings();
      }
      """.trimIndent()
    )

    myFixture.openFileInEditor(mail.containingFile.virtualFile)
    myFixture.findClass("com.example.Mail").navigate(true)
    myFixture.renameElementAtCaret("Post")

    myFixture.openFileInEditor(dao.containingFile.virtualFile)

    myFixture.checkResult(
      //language=JAVA
      """
      package com.example;

      import androidx.room.Dao;
      import androidx.room.Query;

      @Dao
      public interface SomeDao {
        @Query("SELECT * FROM Post WHERE Post MATCH 'foo'") List<String> getStrings();
      }
      """.trimIndent()
    )
  }

  fun testResolvePrimaryIdColumnByDifferentNames() {
    myFixture.addClass(
      """
      package com.example;

      import androidx.room.Entity;
      import androidx.room.PrimaryKey;

      @Entity
      class User {
        @PrimaryKey
        int myId;
      }
      """
    )

    //language=JAVA
    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT rowId, myId, oid FROM User") List<String> getNames();
        }
    """.trimIndent())


    myFixture.moveCaret("|rowId")
    val element = myFixture.elementAtCaret
    myFixture.moveCaret("|myId")
    // use areElementsEquivalent instead of simple equalsTo cause we wrap realPsiElement into [NotRenamableElement]
    assertThat(PsiManager.getInstance(element.project).areElementsEquivalent(element, myFixture.elementAtCaret))
    myFixture.moveCaret("|oid")
    assertThat(PsiManager.getInstance(element.project).areElementsEquivalent(element, myFixture.elementAtCaret))
  }

  fun testCannotRenameColumnThatUserDoesNotDefine() {
    myFixture.addClass(
      """
      package com.example;

      import androidx.room.Entity;
      import androidx.room.PrimaryKey;

      @Entity
      class User {
        @PrimaryKey
        int myId;
      }
      """
    )

    myFixture.configureByText("UserDao.java",
      //language=JAVA
                              """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT rowi<caret>d, myId FROM User") List<String> getStrings();
        }
    """.trimIndent())

    var renameAction = myFixture.testAction(ActionManager.getInstance().getAction(IdeActions.ACTION_RENAME))
    assertThat(renameAction.isEnabledAndVisible).isFalse()

    //check that we still can rename column by name that user explicitly defined
    myFixture.moveCaret("my|Id")
    renameAction = myFixture.testAction(ActionManager.getInstance().getAction(IdeActions.ACTION_RENAME))
    assertThat(renameAction.isEnabledAndVisible).isTrue()
  }

  fun testResolveBuildInTable() {
    myFixture.loadNewFile("com/example/SomeDao.kt", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        interface Dao {
          @Query("SELECT <caret> FROM sqlite_sequence WHERE name = :tableName")
          suspend fun getSequenceNumber(tableName:String) : Long?
        }
    """.trimIndent())

    val columns = myFixture.completeBasic().map { it.lookupString }
    assertThat(columns).containsExactly("name", "seq")
  }
}
