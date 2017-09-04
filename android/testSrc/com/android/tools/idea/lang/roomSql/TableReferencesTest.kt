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

class TableReferencesTest : LightRoomTestCase() {

  fun testDefaultTableName() {
    myFixture.addClass("package com.example; public class NotAnEntity {}")
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.User"))
  }

  fun testCaseInsensitive_unquoted() {
    myFixture.addClass("package com.example; public class NotAnEntity {}")
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM u<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.User"))
  }

  fun testCaseInsensitive_quoted() {
    myFixture.addClass("package com.example; public class NotAnEntity {}")
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM 'u<caret>ser'") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.User"))
  }

  fun testTableNameOverride() {
    myFixture.addClass("package com.example; public class NotAnEntity {}")
    myFixture.addRoomEntity("com.example.User", tableNameOverride = "people")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM <caret>people") List<User> getAll();
        }
    """.trimIndent())

    val referenceTarget = myFixture.elementAtCaret
    assertThat(referenceTarget).isInstanceOf(PsiLiteralExpression::class.java)
    assertThat(referenceTarget.text).isEqualTo("\"people\"")
  }

  fun testMultiResolve() {
    myFixture.addRoomEntity("com.foo.User")
    myFixture.addRoomEntity("com.bar.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    val psiReference = myFixture.file.findReferenceAt(myFixture.caretOffset) as RoomTablePsiReference
    assertThat(psiReference.multiResolve(false).map(ResolveResult::getElement))
        .containsExactly(myFixture.findClass("com.foo.User"), myFixture.findClass("com.bar.User"))
  }

  fun testRename_fromSql() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("Person")

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM Person") List<Person> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.Person"))
  }

  fun testRename_fromSql_quoted() {
    myFixture.addRoomEntity("com.example.Order")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface OrderDao {
          @Query("SELECT * FROM 'O<caret>rder'") List<Order> getAll();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("OrderItem")

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface OrderDao {
          @Query("SELECT * FROM OrderItem") List<OrderItem> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.OrderItem"))
  }

  fun testRename_fromJava() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM User") List<<caret>User> getAll();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("Person")

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM Person") List<Person> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.Person"))
  }

  fun testRename_escaping() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    val newName = "Order" // this is a SQL keyword.

    myFixture.renameElementAtCaret(newName)

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM `$newName`") List<$newName> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.$newName"))
  }

  fun testCodeCompletion_single() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>") List<User> getAll();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM User") List<User> getAll();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_caseSensitivity() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM u<caret>") List<User> getAll();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user") List<User> getAll();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_multiple() {
    myFixture.addRoomEntity("com.example.User", tableNameOverride = "people")
    myFixture.addRoomEntity("com.example.Address")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM <caret>") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { Pair(it.lookupString, it.psiElement) })
        .containsExactly(
            Pair("people", myFixture.findClass("com.example.User")),
            Pair("Address", myFixture.findClass("com.example.Address")))
  }

  fun testCodeCompletion_escaping() {
    myFixture.addRoomEntity("com.example.Address")
    myFixture.addRoomEntity("com.example.Order")
    val userClass = myFixture.addRoomEntity("com.example.User", tableNameOverride = "funny people")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM <caret>") List<User> getAll();
        }
    """.trimIndent())

    val lookupElements = myFixture.completeBasic()

    assertThat(lookupElements.map { Pair(it.lookupString, it.psiElement) })
        .containsExactly(
            Pair("`funny people`", userClass),
            Pair("Address", myFixture.findClass("com.example.Address")),
            Pair("`Order`", myFixture.findClass("com.example.Order"))) // ORDER is a keyword in SQL.

    myFixture.lookup.currentItem = lookupElements.find { it.psiElement === userClass }
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)

    myFixture.checkResult("""
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM `funny people`") List<User> getAll();
        }
    """.trimIndent())
  }

  fun testUsages() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.elementAtCaret).find { it.file!!.language == ROOM_SQL_LANGUAGE }).isNotNull()
  }

  fun testUsages_caseInsensitive() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.User")).find { it.file!!.language == ROOM_SQL_LANGUAGE })
        .isNotNull()
  }

  fun testUsages_tableNameOverride() {
    myFixture.addRoomEntity("com.example.User", tableNameOverride = "people")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM people") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.User")).find { it.file!!.language == ROOM_SQL_LANGUAGE })
        .isNotNull()
  }

  fun testUsages_tableNameOverride_escaping() {
    myFixture.addRoomEntity("com.example.User", tableNameOverride = "foo`bar")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM `foo``bar`") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.User")).find { it.file!!.language == ROOM_SQL_LANGUAGE })
        .isNotNull()
  }

  fun testUsages_tableNameOverride_spaces() {
    myFixture.addRoomEntity("com.example.User", tableNameOverride = "foo bar")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM `foo bar`") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.User")).find { it.file!!.language == ROOM_SQL_LANGUAGE })
        .isNotNull()
  }

  fun testUsages_keyword() {
    myFixture.addRoomEntity("com.example.Order")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public interface OrderDao {
          @Query("SELECT * FROM `Order`") List<Order> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.Order")).find { it.file!!.language == ROOM_SQL_LANGUAGE })
        .isNotNull()
  }
}
