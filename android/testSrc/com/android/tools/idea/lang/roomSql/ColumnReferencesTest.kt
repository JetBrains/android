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
import com.intellij.psi.ResolveResult

class ColumnReferencesTest : LightRoomTestCase() {

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
          @Query("SELECT 'fulln<caret>ame' FROM User") List<String> getNames();
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

  fun testMultiResolve() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")
    myFixture.addRoomEntity("com.example.Book", "id" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    val psiReference = myFixture.file.findReferenceAt(myFixture.caretOffset) as RoomColumnPsiReference
    assertThat(psiReference.multiResolve(false).map(ResolveResult::getElement))
        .containsExactly(
            myFixture.findField("com.example.User", "id"),
            myFixture.findField("com.example.Book", "id")
        )
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
          @Query("SELECT 'c<caret>ount' FROM 'Order'") List<Integer> getCounts();
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

    assertThat(myFixture.findUsages(myFixture.elementAtCaret).find { it.file!!.language == ROOM_SQL_LANGUAGE })
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

    assertThat(myFixture.findUsages(myFixture.elementAtCaret).find { it.file!!.language == ROOM_SQL_LANGUAGE }!!).isNotNull()

    assertThat(
        myFixture.findUsages(myFixture.findField("com.example.User", "name"))
            .find { it.file!!.language == ROOM_SQL_LANGUAGE })
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
            .find { it.file!!.language == ROOM_SQL_LANGUAGE })
        .isNotNull()
  }

  fun testUsages_tableNameOverride() {
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
            .find { it.file!!.language == ROOM_SQL_LANGUAGE })
        .isNotNull()
  }

  fun testUsages_tableNameOverride_escaping() {
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
          @Query("SELECT 'user''s name' FROM User") List<String> getNames();
        }
    """.trimIndent())

    assertThat(
        myFixture.findUsages(myFixture.findField("com.example.User", "fullName"))
            .find { it.file!!.language == ROOM_SQL_LANGUAGE })
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
          @Query("SELECT 'desc' FROM Item") List<String> getDescriptions();
        }
    """.trimIndent())

    assertThat(
        myFixture.findUsages(myFixture.findField("com.example.Item", "desc"))
            .find { it.file!!.language == ROOM_SQL_LANGUAGE })
        .isNotNull()
  }
}
