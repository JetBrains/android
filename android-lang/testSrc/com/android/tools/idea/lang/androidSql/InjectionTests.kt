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
package com.android.tools.idea.lang.androidSql

import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.android.AndroidFacetProjectDescriptor
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase
import org.jetbrains.kotlin.idea.KotlinFileType

abstract class RoomQueryInjectionTest : LightJavaCodeInsightFixtureAdtTestCase() {

  private val injectionFixture: InjectionTestFixture by lazy { InjectionTestFixture(myFixture) }

  abstract val useJavaSource: Boolean

  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture, useJavaSource)
  }

  fun testSanityCheck() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      """
        package com.example;

        import androidx.room.Query;

        interface UserDao {
          @com.example.MadeUp("select * $caret from User")
          List<User> findAll();
        }""".trimIndent()
    )

    injectionFixture.assertInjectedLangAtCaret(null)
  }

  fun testSimpleQuery() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      """
        package com.example;

        import androidx.room.Query;

        interface UserDao {
          @Query("select * $caret from User")
          List<User> findAll();
        }""".trimIndent()
    )

    injectionFixture.assertInjectedLangAtCaret(AndroidSqlLanguage.INSTANCE.id)
  }

  fun testSimpleQueryKotlin() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      """
        package com.example

        import androidx.room.Query

        interface UserDao {
          @Query("select * $caret from User")
          fun findAll(): List<User>
        }""".trimIndent()
    )

    injectionFixture.assertInjectedLangAtCaret(AndroidSqlLanguage.INSTANCE.id)
  }

  fun testDatabaseView() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      """
        package com.example;

        import androidx.room.DatabaseView;

        interface UserDao {
          @DatabaseView("select * $caret from User")
          List<User> findAll();
        }""".trimIndent()
    )

    injectionFixture.assertInjectedLangAtCaret(AndroidSqlLanguage.INSTANCE.id)
  }

  fun testConcatenation() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      """
        package com.example;

        import androidx.room.Query;

        interface UserDao {
          String TABLE = "User";

          @Query("select ${caret}* from " + TABLE + " where id = :id")
          User findById(int id);
        }
        """.trimIndent()
    )

    injectionFixture.assertInjectedLangAtCaret(AndroidSqlLanguage.INSTANCE.id)
    assertThat(injectionFixture.getAllInjections().last().second.text).isEqualTo("select * from User where id = :id")
  }

  fun testStringConstants() {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
      """
        package com.example;

        import androidx.room.Query;

        interface UserDao {
          String ENTITY = "User";
          String TABLE = ENTITY + "Table";

          @Query("select ${caret}* from " + TABLE)
          List<User> findAll();
        }
        """.trimIndent()
    )

    injectionFixture.assertInjectedLangAtCaret(AndroidSqlLanguage.INSTANCE.id)
    assertThat(injectionFixture.getAllInjections().last().second.text).isEqualTo("select * from UserTable")
  }
}

class RoomQueryInjectionJavaTest : RoomQueryInjectionTest() {
  override val useJavaSource = true
}

class RoomQueryInjectionKotlinTest : RoomQueryInjectionTest() {
  override val useJavaSource = false
}

class OtherApisInjectionTest : LightJavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture)
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = AndroidFacetProjectDescriptor

  private val injectionFixture: InjectionTestFixture by lazy { InjectionTestFixture(myFixture) }

  fun testSqliteDatabase() {
    myFixture.addClass(
      """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.execSQL("delete${caret} from User");
          }
        }
        """.trimIndent()
    ).also {
      myFixture.configureFromExistingVirtualFile(it.containingFile.virtualFile)
    }

    injectionFixture.assertInjectedLangAtCaret(AndroidSqlLanguage.INSTANCE.id)
  }

  fun testSqliteDatabase_nested() {
    myFixture.addClass(
      """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.execSQL(getQuery("${caret}foo"));
          }
        }
        """.trimIndent()
    ).also {
      myFixture.configureFromExistingVirtualFile(it.containingFile.virtualFile)
    }

    injectionFixture.assertInjectedLangAtCaret(null)
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
      myFixture.configureFromExistingVirtualFile(it.containingFile.virtualFile)
    }

    val onlyFile = injectionFixture.getAllInjections().single().second
    assertThat(onlyFile.language).isEqualTo(AndroidSqlLanguage.INSTANCE)
    assertThat(onlyFile.text).isEqualTo("select * from User")
  }
}
