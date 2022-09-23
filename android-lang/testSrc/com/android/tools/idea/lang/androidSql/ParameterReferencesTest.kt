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

import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiParameter
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase

class ParameterReferencesTest : LightJavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture)
  }

  fun testReference_single() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = :<caret>nameToLookFor")
          List<User> getByName(String nameToLookFor);
        }
    """.trimIndent())

    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(PsiParameter::class.java)
    assertThat(elementAtCaret.text).isEqualTo("String nameToLookFor")

    (elementAtCaret as NavigatablePsiElement).navigate(true)

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = :nameToLookFor")
          List<User> getByName(String <caret>nameToLookFor);
        }
    """.trimIndent())
  }

  fun testReference_multiple() {
    myFixture.addRoomEntity("com.example.User", "age" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE age BETWEEN :min AND :<caret>max")
          List<User> findByAge(int min, int max);
        }
    """.trimIndent())

    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(PsiParameter::class.java)
    assertThat(elementAtCaret.text).isEqualTo("int max")

    (elementAtCaret as NavigatablePsiElement).navigate(true)

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE age BETWEEN :min AND :max")
          List<User> findByAge(int min, int <caret>max);
        }
    """.trimIndent())
  }

  fun testCompletionAfterColon_single() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = :n<caret>")
          List<User> getByName(String nameToLookFor);
        }
    """.trimIndent())

    myFixture.completeBasic()

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = :nameToLookFor")
          List<User> getByName(String nameToLookFor);
        }
    """.trimIndent())
  }

  fun testCompletionAfterColon_none() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name = :<caret>")
          List<User> getAll();
        }
    """.trimIndent())

    assertThat(myFixture.completeBasic()).isEmpty()
  }

  fun testCompletionAfterColon_multiple() {
    myFixture.addRoomEntity("com.example.User", "age" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user")
          List<User> someOtherMethod(String otherArg);

          @Query("SELECT * FROM user WHERE age BETWEEN :<caret>")
          List<User> findByAge(int min, int max);
        }
    """.trimIndent())

    val lookupElements = myFixture.completeBasic()

    assertThat(lookupElements.map { it.lookupString }).containsExactly("min", "max")
    assertTrue(lookupElements.map { it.psiElement }.all { it is PsiParameter })
  }

  fun testCompletionNoColon() {
    myFixture.addRoomEntity("com.example.User", "age" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user")
          List<User> someOtherMethod(String otherArg);

          @Query("SELECT * FROM user WHERE <caret>")
          List<User> findByAge(int min, int max);
        }
    """.trimIndent())

    val lookupElements = myFixture.completeBasic()

    assertThat(lookupElements).hasLength(1)
    assertThat(lookupElements.single().lookupString).isEqualTo("age")
  }

  fun testRename() {
    myFixture.addRoomEntity("com.example.User", "age" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE age BETWEEN :min AND :<caret>max")
          List<User> findByAge(int min, int max);
        }
    """.trimIndent())

    myFixture.renameElementAtCaret("high")

    myFixture.checkResult("""
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE age BETWEEN :min AND :high")
          List<User> findByAge(int min, int high);
        }
    """.trimIndent())
  }
}
