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
package com.android.tools.idea.lang.roomSql

import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiLiteralExpression

class ColumnReferencesTest : RoomLightTestCase() {

  fun testDefaultColumnName() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT n<caret>ame FROM User") List<String> getNames();
        }
    """.trimIndent())

    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "name"))
  }

  fun testCaseInsensitive_unquoted() {
    myFixture.addRoomEntity("com.example.User", "fullName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d from user, book") List<Integer> getIds();
        }
    """.trimIndent())

    // User is first in the FROM clause, so it will be picked for resolving "id". At compile time this should fail.
    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "id"))
  }

  fun testRename_fromSql() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("user_id")

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface OrderDao {
          @Query("SELECT `c<caret>ount` FROM 'Order'") List<Integer> getCounts();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.Order", "count"))
    myFixture.renameElementAtCaret("amount")

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    val newName = "order" // this is a SQL keyword.
    myFixture.renameElementAtCaret(newName)

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT `$newName` FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", newName))
  }

  fun testCodeCompletion_single() {
    myFixture.addRoomEntity("com.example.User",  "firstName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT f<caret>") List<String> getNames();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT firstName") List<String> getNames();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_caseSensitivity() {
    myFixture.addRoomEntity("com.example.User",  "firstName" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT firstn<caret>") List<String> getNames();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT `check`") List<User> getAll();
        }
    """.trimIndent())
  }

  fun testUsages() {
    myFixture.addRoomEntity("com.example.User",  "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT n<caret>ame FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.elementAtCaret).find { it.file!!.language == RoomSqlLanguage.INSTANCE })
        .isNotNull()
  }

  fun testUsages_privateFields() {
    myFixture.addClass("""
      package com.example;

      import android.arch.persistence.room.Entity;

      @Entity
      public class User { private String name; }
      """.trimIndent())

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT n<caret>ame FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.elementAtCaret).find { it.file!!.language == RoomSqlLanguage.INSTANCE }!!).isNotNull()

    assertThat(
        myFixture.findUsages(myFixture.findField("com.example.User", "name"))
            .find { it.file!!.language == RoomSqlLanguage.INSTANCE })
        .isNotNull()
  }

  fun testUsages_caseInsensitive() {
    myFixture.addRoomEntity("com.example.User",  "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT Name FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(
        myFixture.findUsages(myFixture.findField("com.example.User", "name"))
            .find { it.file!!.language == RoomSqlLanguage.INSTANCE })
        .isNotNull()
  }

  fun testUsages_nameOverride() {
    myFixture.addRoomEntity(
        "com.example.User",
        FieldDefinition("id", "int"), FieldDefinition("fullName", "String", columnName = "full_name")
    )

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT full_name FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(
        myFixture.findUsages(myFixture.findField("com.example.User", "fullName"))
            .find { it.file!!.language == RoomSqlLanguage.INSTANCE })
        .isNotNull()
  }

  fun testUsages_nameOverride_escaping() {
    myFixture.addRoomEntity(
        "com.example.User",
        FieldDefinition("fullName", "String", columnName = "user's name")
    )

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT `user's name` FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(
        myFixture.findUsages(myFixture.findField("com.example.User", "fullName"))
            .find { it.file!!.language == RoomSqlLanguage.INSTANCE })
        .isNotNull()
  }

  fun testUsages_keyword() {
    myFixture.addRoomEntity("com.example.Item", "desc" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface ItemDao {
          @Query("SELECT `desc` FROM Item") List<String> getDescriptions();
        }
    """.trimIndent())

    assertThat(
        myFixture.findUsages(myFixture.findField("com.example.Item", "desc"))
            .find { it.file!!.language == RoomSqlLanguage.INSTANCE })
        .isNotNull()
  }

  fun testQualifiedColumns() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT a FROM Aaa WHERE a IN (SELECT b FROM Bbb WHERE <caret>)") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("a", "b")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT a FROM Aaa WHERE a IN (SELECT b FROM Bbb WHERE b IN (SELECT c FROM Ccc WHERE <caret>))") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("a", "b", "c")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT a FROM Aaa WHERE a IN (SELECT b FROM Bbb WHERE b IN (SELECT c FROM Ccc WHERE <caret>))") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("a", "b", "c")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH t1 AS (VALUES(1)) SELECT a FROM Aaa WHERE a IN (WITH t2 AS (VALUES(2)) SELECT b FROM <caret>)")
          List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("Aaa", "Bbb", "Ccc", "t1", "t2")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT (SELECT min(a) FROM aaa), (SELECT max(a) FROM <caret>)") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("Aaa")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM user WHERE score=(SELECT min(score) FROM <caret>)")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM user WHERE score=(SELECT min(<caret>) FROM user)")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("score")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("UPDATE user SET alive=0 WHERE score=(SELECT min(score) FROM <caret>)")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("UPDATE user SET alive=0 WHERE score=(SELECT min(<caret>) FROM user)")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("score", "alive")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH losing_score AS (SELECT min(score) FROM user) UPDATE user SET alive=0 WHERE score IN <caret>")
          void deleteLosers();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("User", "losing_score")
  }

  fun testAliasRenaming() {
    myFixture.addRoomEntity("com.example.User","id" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT id AS user_id FROM user) SELECT user_id FROM ids")
          List<Integer> getIds();
        }
    """.trimIndent())
  }

  fun testWithTableRenaming_columns() {
    myFixture.addRoomEntity("com.example.User","id" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH ids(user_id) AS (SELECT id FROM user) SELECT user_id FROM ids")
          List<Integer> getIds();
        }
    """.trimIndent())
  }

  fun testParserRecovery() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

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

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT fullName FROM User ORDER BY i<caret>d") List<String> getNames();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("com.example.User", "id"))
  }
}
