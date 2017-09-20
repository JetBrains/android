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

class UnresolvedRoomSqlReferenceInspectionTest : LightRoomTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UnresolvedRoomSqlReferenceInspection::class.java)
  }

  fun testInvalidColumn() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE <error descr="Cannot resolve symbol 'age'">age</error> > 18")
          List<User> getAdults();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testValidColumn() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name IS NOT NULL")
          List<User> getUsersWithName();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testInvalidTable() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM <error>madeup</error>")
          List<User> getUsersWithName();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testIncompleteQuery_validColumn() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT name")
          List<User> getUsersWithName();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testIncompleteQuery_invalidColumn() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT madeup")
          List<User> getUsersWithName();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testSubquery_valid() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT n * 2 FROM (SELECT age AS n from user)")
          List<Integer> getNumbers();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testSubquery_invalidOuterQuery() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT madeup * 2 FROM (SELECT age AS n from user)")
          List<Integer> getNumbers();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testSubquery_invalidInnerQuery() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT n * 2 FROM (SELECT <error>madeup</error> AS n from user)")
          List<Integer> getNumbers();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }
}