// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.lang.androidSql

import com.android.tools.idea.lang.androidSql.room.RoomBindParameterSyntaxInspection
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.android.AndroidFacetProjectDescriptor
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase

class AndroidSqlBindParameterSyntaxInspectionTest : LightJavaCodeInsightFixtureAdtTestCase() {

  /** Uses a descriptor that gives us the Android SDK. */
  override fun getProjectDescriptor(): LightProjectDescriptor = AndroidFacetProjectDescriptor

  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture)
    myFixture.enableInspections(RoomBindParameterSyntaxInspection::class.java)
  }

  fun testSanity() {
    // Sanity test: make sure queries with parsing errors are still highlighted.
    myFixture.configureByText("Utils.java", """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.rawQuery("SELECT * FROM user WHERE name =<error> </error>::foo", new String[] {"foo"});
          }
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testQuestionMark() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = <error descr="Room only supports named parameters with a leading colon, e.g. :argName.">?</error>")
          List<User> getByName(String name);
        }
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = <error descr="Room only supports named parameters with a leading colon, e.g. :argName.">?1</error>")
          List<User> getByName(String name);
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testQuestionMarkSqliteDatabase() {
    myFixture.configureByText("Utils.java", """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.rawQuery("SELECT * FROM user WHERE name = ?", new String[] {"foo"});
          }
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testAtSign() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = <error descr="Room only supports named parameters with a leading colon, e.g. :argName.">@name</error>")
          List<User> getByName(String name);
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testAtSignSqliteDatabase() {
    myFixture.configureByText("Utils.java", """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.rawQuery("SELECT * FROM user WHERE name = @name", new String[] {"foo"});
          }
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testColon() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = :name")
          List<User> getByName(String name);
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testColonSqliteDatabase() {
    myFixture.configureByText("Utils.java", """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.rawQuery("SELECT * FROM user WHERE name = :name", new String[] {"foo"});
          }
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testDollar() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = <error descr="Room only supports named parameters with a leading colon, e.g. :argName.">${'$'}name</error>")
          List<User> getByName(String name);
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testDollarSqliteDatabase() {
    myFixture.configureByText("Utils.java", """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.rawQuery("SELECT * FROM user WHERE name = ${'$'}name", new String[] {"foo"});
          }
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }
}
