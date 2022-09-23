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

import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase

class EditingTest : LightJavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture)
  }
  
  fun testParens() {
    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS <caret>")
          void runQuery();
        }
    """.trimIndent())

    myFixture.type('(')

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("WITH ids AS (<caret>)")
          void runQuery();
        }
    """.trimIndent())
  }

  fun testStrings() {
    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT <caret>")
          void runQuery();
        }
    """.trimIndent())

    myFixture.type('\'')

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT '<caret>'")
          void runQuery();
        }
    """.trimIndent())
  }

  fun testBackticks() {
    myFixture.configureByText("UserDao.java", """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT <caret>")
          void runQuery();
        }
    """.trimIndent())

    myFixture.type('`')

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT `<caret>`")
          void runQuery();
        }
    """.trimIndent())
  }
}
