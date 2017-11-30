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

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiLanguageInjectionHost.Shred
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil.enumerate
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.*

private fun JavaCodeInsightTestFixture.checkNoInjection(text: String) {
  assertFalse(InjectedLanguageUtil.hasInjections(findElementByText(text, PsiLanguageInjectionHost::class.java)!!))
}

private fun JavaCodeInsightTestFixture.checkInjection(text: String, block: (PsiFile, List<Shred>) -> Unit) {
  val host = findElementByText(text, PsiLanguageInjectionHost::class.java)!!
  var injectionsCount = 0

  assertTrue(InjectedLanguageUtil.hasInjections(host))

  enumerate(host) { injectedPsi, places ->
    assertEquals("More than one injection", 0, injectionsCount++)
    block.invoke(injectedPsi, places)
  }

  assertTrue(injectionsCount > 0)
}

class RoomQueryInjectionTest : RoomLightTestCase() {

  fun testSanityCheck() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.room.Query;

        interface UserDao {
          @com.example.MadeUp("select * from User")
          List<User> findAll();
        }""".trimIndent()
    )

    myFixture.checkNoInjection("* from User")
  }

  fun testSimpleQuery() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.room.Query;

        interface UserDao {
          @Query("select * from User")
        List<User> findAll();
      }""".trimIndent()
    )

    myFixture.checkInjection("* from") { psi, _ ->
      assertSame(RoomSqlLanguage.INSTANCE, psi.language)
      assertEquals("select * from User", psi.text)
    }
  }

  fun testConcatenation() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.room.Query;

        interface UserDao {
          String TABLE = "User";

          @Query("select * from " + TABLE + " where id = :id")
          User findById(int id);
        }
        """.trimIndent()
    )

    myFixture.checkInjection("* from") { psi, places ->
      assertSame(RoomSqlLanguage.INSTANCE, psi.language)
      assertEquals("select * from User where id = :id", psi.text)
      assertEquals(2, places.size)
    }
  }

  fun testStringConstants() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.room.Query;

        interface UserDao {
          String ENTITY = "User";
          String TABLE = ENTITY + "Table";

          @Query("select * from " + TABLE)
          List<User> findAll();
        }
        """.trimIndent()
    )

    myFixture.checkInjection("* from") { psi, _ ->
      assertSame(RoomSqlLanguage.INSTANCE, psi.language)
      assertEquals("select * from UserTable", psi.text)
    }
  }
}

class OtherApisInjectionTest : RoomLightTestCase() {

  override fun setUp() {
    super.setUp()

    if (AndroidFacet.getInstance(myModule) == null) {
      AndroidTestCase.addAndroidFacet(myModule)
    }
  }

  fun testSqliteDatabase() {
    myFixture.addClass(
        """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.execSQL("delete from User");
          }
        }
        """.trimIndent()
    ).also {
      myFixture.openFileInEditor(it.containingFile.virtualFile)
    }


    myFixture.checkInjection("delete from") { psi, _ ->
      assertSame(RoomSqlLanguage.INSTANCE, psi.language)
      assertEquals("delete from User", psi.text)
    }
  }

  fun testSqliteDatabase_nested() {
    myFixture.addClass(
        """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.execSQL(getQuery("foo"));
          }
        }
        """.trimIndent()
    ).also {
      myFixture.openFileInEditor(it.containingFile.virtualFile)
    }

    myFixture.checkNoInjection("foo")
  }

  fun testSqliteDatabase_wrongArgument() {
    myFixture.addClass(
        """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.rawQueryWithFactory(null, "select * from User", null, "tableName", null);
          }
        }
        """.trimIndent()
    ).also {
      myFixture.openFileInEditor(it.containingFile.virtualFile)
    }

    myFixture.checkInjection("* from") { psi, _ ->
      assertSame(RoomSqlLanguage.INSTANCE, psi.language)
    }

    myFixture.checkNoInjection("tableName")
  }
}
