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

import com.android.tools.idea.lang.androidSql.psi.AndroidSqlTableAliasName
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlTableDefinitionName
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiLiteralExpression
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase

class TableReferencesTest : LightJavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture)
  }

  fun testDefaultTableName() {
    myFixture.addClass("package com.example; public class NotAnEntity {}")
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

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

        import androidx.room.Dao;
        import androidx.room.Query;

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

        import androidx.room.Dao;
        import androidx.room.Query;

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

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM <caret>people") List<User> getAll();
        }
    """.trimIndent())

    val referenceTarget = myFixture.elementAtCaret
    assertThat(referenceTarget).isInstanceOf(PsiLiteralExpression::class.java)
    assertThat(referenceTarget.text).isEqualTo("\"people\"")
  }

  fun testRename_fromSql() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("Person")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

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

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface OrderDao {
          @Query("SELECT * FROM 'O<caret>rder'") List<Order> getAll();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("OrderItem")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

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

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM User") List<<caret>User> getAll();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("Person")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

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

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    val newName = "Order" // this is a SQL keyword.

    myFixture.renameElementAtCaret(newName)

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM `$newName`") List<$newName> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.$newName"))
  }

  fun testCodeCompletion_select() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>") List<User> getAll();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM User") List<User> getAll();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_update() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("UPDATE U<caret>") void update();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("UPDATE User") void update();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_delete() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM U<caret>") void delete();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM User") void delete();
        }
    """.trimIndent())
  }

  fun testCodeCompletion_caseSensitivity() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM u<caret>") List<User> getAll();
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

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

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM <caret>") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString to it.psiElement })
      .containsExactly(
        "people" to myFixture.findClass("com.example.User"),
        "Address" to myFixture.findClass("com.example.Address"))
  }

  fun testCodeCompletion_escaping() {
    myFixture.addRoomEntity("com.example.Address")
    myFixture.addRoomEntity("com.example.Order")
    val userClass = myFixture.addRoomEntity("com.example.User", tableNameOverride = "funny people")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM <caret>") List<User> getAll();
        }
    """.trimIndent())

    val lookupElements = myFixture.completeBasic()

    assertThat(lookupElements.map { it.lookupString to it.psiElement })
      .containsExactly(
        "`funny people`" to userClass,
        "Address" to myFixture.findClass("com.example.Address"),
        "`Order`" to myFixture.findClass("com.example.Order")) // ORDER is a keyword in SQL.

    myFixture.lookup.currentItem = lookupElements.find { it.psiElement === userClass }
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

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

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.elementAtCaret).find { it.file!!.language == AndroidSqlLanguage.INSTANCE }).isNotNull()
  }

  fun testUsages_caseInsensitive() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.User")).find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_tableNameOverride() {
    myFixture.addRoomEntity("com.example.User", tableNameOverride = "people")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM people") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.User")).find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_tableNameOverride_escaping() {
    myFixture.addRoomEntity("com.example.User", tableNameOverride = "foo`bar")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM `foo``bar`") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.User")).find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_tableNameOverride_spaces() {
    myFixture.addRoomEntity("com.example.User", tableNameOverride = "foo bar")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM `foo bar`") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.User")).find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testUsages_keyword() {
    myFixture.addRoomEntity("com.example.Order")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface OrderDao {
          @Query("SELECT * FROM `Order`") List<Order> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.findUsages(myFixture.findClass("com.example.Order")).find { it.file!!.language == AndroidSqlLanguage.INSTANCE })
      .isNotNull()
  }

  fun testQualifiedColumns() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT u<caret>ser.name FROM user") List<String> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.User"))
  }

  fun testAliases() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT a<caret>lias.name FROM user AS alias") List<String> getAll();
        }
    """.trimIndent())

    val elementAtCaret = myFixture.elementAtCaret
    // Unfortunately it seems that calling navigate() on elementAtCaret doesn't work in the test fixture (probably because of injection).
    // Let's assert that it's actually the element we want.
    assertThat(elementAtCaret).isInstanceOf(AndroidSqlTableAliasName::class.java)
    assertThat(elementAtCaret.parent.text).isEqualTo("user AS alias")
  }

  fun testAliases_hiding() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT u<caret>ser.name FROM user AS alias") List<String> getAll();
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
          @Query("SELECT * FROM user u JOIN book b ON u.uid = <caret>b.bid") List<User> getAll();
        }
    """.trimIndent())

    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(AndroidSqlTableAliasName::class.java)
    assertThat(elementAtCaret.parent.text).isEqualTo("book b")
  }

  fun testWithClause_newTable() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT uid FROM user) SELECT uid FROM id<caret>s") List<Integer> getAll();
        }
    """.trimIndent())

    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(AndroidSqlTableDefinitionName::class.java)
    assertThat(elementAtCaret.text).isEqualTo("ids")
    assertThat(elementAtCaret.parent.parent.text).startsWith("ids AS (SELECT ")
  }

  fun testWithClause_subquery() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT uid FROM u<caret>ser) SELECT uid FROM ids") List<Integer> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.User"))
  }

  fun testWithClause_completion() {
    myFixture.addRoomEntity("com.example.User", "uid" ofType "int")
    myFixture.addRoomEntity("com.example.Book", "bid" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT uid FROM u<caret>ser) SELECT * FROM <caret>") List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("User", "Book", "ids")
  }

  fun testViews() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT id from user) DELETE FROM <caret>")
          void deleteAdults();
        }
    """.trimIndent())

    // "ids" is a view, you cannot delete from it.
    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly("User")
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
          @Query("SELECT u.id FROM user <caret>u")
          List<Integer> getIds();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("u1")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT u1.id FROM user u1")
          List<Integer> getIds();
        }
    """.trimIndent())
  }

  fun testAliasRenaming_join() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT u.id FROM user <caret>u JOIN user u2")
          List<Integer> getIds();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("u1")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT u1.id FROM user u1 JOIN user u2")
          List<Integer> getIds();
        }
    """.trimIndent())
  }

  fun testWithTableRenaming_noColumns() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (SELECT id FROM user) SELECT id FROM <caret>ids")
          List<Integer> getIds();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("user_ids")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH user_ids AS (SELECT id FROM user) SELECT id FROM user_ids")
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
          @Query("WITH ids(x) AS (SELECT id FROM user) SELECT x FROM <caret>ids")
          List<Integer> getIds();
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("user_ids")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH user_ids(x) AS (SELECT id FROM user) SELECT x FROM user_ids")
          List<Integer> getIds();
        }
    """.trimIndent())
  }

  fun testViews_inDao() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")

    myFixture.addClass("""
      package com.example;

      import androidx.room.DatabaseView;

      @DatabaseView(value = "select id from user", viewName = "ids")
      public class Ids {
        public int id;
      }
    """.trimIndent())

    myFixture.configureByText("SomeDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface SomeDao {
          @Query("SELECT * FROM <caret>")
          List<Integer> getIds();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString to it.psiElement })
      .containsExactly(
        "ids" to myFixture.findClass("com.example.Ids"),
        "User" to myFixture.findClass("com.example.User"))
  }

  fun testViews_inTheViewItself() {
    myFixture.addRoomEntity("com.example.User", "id" ofType "int")

    myFixture.configureByText("Ids.java", """
      package com.example;

      import androidx.room.DatabaseView;

      @DatabaseView(value = "select id from $caret", viewName = "ids")
      public class Ids {
        public int id;
      }
    """.trimIndent())

    assertThat(myFixture.completeBasic().map { it.lookupString to it.psiElement })
      .containsExactly("User" to myFixture.findClass("com.example.User"))
  }

  fun test_duplicatedTableNames() {
    myFixture.addClass("package com.example; public class NotAnEntity {}")
    myFixture.addRoomEntity("com.example.first.User")
    myFixture.addRoomEntity("com.example.second.User")

    myFixture.addClass("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Database;
        import androidx.room.Query;

        @Database(entities = {com.example.first.User.class}, version = 1)
        public abstract class AppDatabaseWithFirstUser extends RoomDatabase {
            public abstract UserDaoWithFirstUser userDao();
        }
    """.trimIndent())

    myFixture.addClass( """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Database;
        import androidx.room.Query;

        @Database(entities = {com.example.second.User.class}, version = 1)
        public abstract class AppDatabaseWithSecondUser extends RoomDatabase {
            public abstract UserDaoWithSecondUser userDao();
        }
    """.trimIndent())


    var dao = myFixture.addClass( """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDaoWithFirstUser {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(dao.containingFile.virtualFile)

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.first.User"))

    dao = myFixture.addClass( """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDaoWithSecondUser {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(dao.containingFile.virtualFile)

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("com.example.second.User"))


  }
}
