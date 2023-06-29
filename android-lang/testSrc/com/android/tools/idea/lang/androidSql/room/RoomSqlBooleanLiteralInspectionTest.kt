/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.lang.androidSql.room

import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File

@RunWith(Parameterized::class)
@RunsInEdt
class RoomSqlBooleanLiteralInspectionTest(private val minSdk: Int, private val expectWarning: Boolean) {

  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameters(name = "minSdk={0}")
    val data = listOf(
      arrayOf(28, /* expectWarning = */ true),
      arrayOf(29, /* expectWarning = */ true),
      arrayOf(30, /* expectWarning = */ false),
      arrayOf(31, /* expectWarning = */ false),
      arrayOf(32, /* expectWarning = */ false))
  }

  @get:Rule
  val projectRule = AndroidProjectRule
    .withAndroidModels(
      prepareProjectSources = { dir -> assertThat(File(dir, "src").mkdirs()).isTrue() },
      AndroidModuleModelBuilder(
        gradlePath = ":",
        selectedBuildVariant = "debug",
        projectBuilder = createAndroidProjectBuilderForDefaultTestProjectStructure()
          .withMinSdk { this@RoomSqlBooleanLiteralInspectionTest.minSdk }
      )
    )
    .onEdt()

  val myFixture: JavaCodeInsightTestFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Before
  fun setUp() {
    // Most RoomSql tests use `createStubRoomClasses` to define the required Room interfaces. But this test requires an AndroidModel, and
    // the setup ends up requiring all source files live under the "src" directory. `createStubRoomClasses` doesn't place the sources under
    // that directory, and updating it would require changing many tests. Instead, just manually creating a few interfaces here suffices.
    myFixture.addFileToProject(
      "src/androidx/room/Query.java",
      //language=java
      """
      package androidx.room;
      public @interface Query { String value(); }
      """.trimIndent())

    myFixture.addFileToProject(
      "src/androidx/room/Dao.java",
      //language=java
      """
      package androidx.room;
      public @interface Dao {}
      """.trimIndent())

    myFixture.enableInspections(RoomSqlBooleanLiteralInspection::class.java)
  }

  @Test
  fun booleanLiteral() {
    val booleanLiteralText = if (expectWarning) {
      """<warning descr="Boolean literals require API level 30 (current min is $minSdk).">TRUE</warning>"""
    } else {
      """TRUE"""
    }

    val file = myFixture.addFileToProject(
      "src/com/example/UserDao.java",
      //language=java
      """
      package com.example;

      import androidx.room.Dao;
      import androidx.room.Query;
      import java.util.List;

      @Dao
      public interface UserDao {
        @Query("SELECT * FROM user WHERE isActive = $booleanLiteralText")
        List<Object> getObjects();
      }
      """.trimIndent())
    myFixture.openFileInEditor(file.virtualFile)

    myFixture.checkHighlighting()
  }

  @Test
  fun booleanLiteralInPragma() {
    // true/false in pragma statements is supported regardless of API level, so ensure it's never highlighted as a warning.
    val file = myFixture.addFileToProject(
      "src/com/example/UserDao.java",
      //language=java
      """
      package com.example;

      import androidx.room.Dao;
      import androidx.room.Query;
      import java.util.List;

      @Dao
      public interface UserDao {
        @Query("PRAGMA foreign_keys=true")
        List<Object> getObjects();
      }
      """.trimIndent())
    myFixture.openFileInEditor(file.virtualFile)

    myFixture.checkHighlighting()
  }

  @Test
  fun applyQuickFixForTrue() {
    // For API levels that don't show the warning, there's nothing to verify.
    if (!expectWarning) return

    val file = myFixture.addFileToProject(
      "src/com/example/UserDao.java",
      //language=java
      """
      package com.example;

      import androidx.room.Dao;
      import androidx.room.Query;
      import java.util.List;

      @Dao
      public interface UserDao {
        @Query("SELECT * FROM user WHERE isActive = TRUE")
        List<Object> getObjects();
      }
      """.trimIndent())
    myFixture.openFileInEditor(file.virtualFile)
    myFixture.moveCaret("SELECT * FROM user WHERE isActive = TR|UE")

    val intention = myFixture.getAvailableIntention("Replace Boolean literal 'TRUE' with '1'")
    assertThat(intention).isNotNull()

    CommandProcessor.getInstance().executeCommand(
      myFixture.project,
      { runWriteAction { intention!!.invoke(myFixture.project, myFixture.editor, file) } },
      /* name = */ "Apply Quick Fix",
      /* groupId = */ null
    )

    myFixture.checkResult(
      //language=java
      """
      package com.example;

      import androidx.room.Dao;
      import androidx.room.Query;
      import java.util.List;

      @Dao
      public interface UserDao {
        @Query("SELECT * FROM user WHERE isActive = 1")
        List<Object> getObjects();
      }
      """.trimIndent())
  }

  @Test
  fun applyQuickFixForFalse() {
    // For API levels that don't show the warning, there's nothing to verify.
    if (!expectWarning) return

    val file = myFixture.addFileToProject(
      "src/com/example/UserDao.java",
      //language=java
      """
      package com.example;

      import androidx.room.Dao;
      import androidx.room.Query;
      import java.util.List;

      @Dao
      public interface UserDao {
        @Query("SELECT * FROM user WHERE isActive = FALSE")
        List<Object> getObjects();
      }
      """.trimIndent())
    myFixture.openFileInEditor(file.virtualFile)
    myFixture.moveCaret("SELECT * FROM user WHERE isActive = FAL|SE")

    val intention = myFixture.getAvailableIntention("Replace Boolean literal 'FALSE' with '0'")
    assertThat(intention).isNotNull()

    CommandProcessor.getInstance().executeCommand(
      myFixture.project,
      { runWriteAction { intention!!.invoke(myFixture.project, myFixture.editor, file) } },
      /* name = */ "Apply Quick Fix",
      /* groupId = */ null
    )

    myFixture.checkResult(
      //language=java
      """
      package com.example;

      import androidx.room.Dao;
      import androidx.room.Query;
      import java.util.List;

      @Dao
      public interface UserDao {
        @Query("SELECT * FROM user WHERE isActive = 0")
        List<Object> getObjects();
      }
      """.trimIndent())
  }
}
