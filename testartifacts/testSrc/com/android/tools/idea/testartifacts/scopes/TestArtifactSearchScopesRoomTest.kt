/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.scopes

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.onEdt
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.writeChild
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class TestArtifactSearchScopesRoomTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  @Before
  fun setup() {
    projectRule.loadProject(SIMPLE_APPLICATION) { root ->
      root.resolve("app/build.gradle").let {
        WriteCommandAction.runWriteCommandAction(projectRule.project) {
          it.appendText("\ndependencies { implementation \"android.arch.persistence.room:runtime:1.0.0\" }")
          root.createEntity(className = "User", path = "app/src/main/java/com/example/User.java")
          root.createEntity(className = "TestUser", path = "app/src/test/java/com/example/TestUser.java")
          root.createEntity(className = "AndroidTestUser", path = "app/src/androidTest/java/com/example/AndroidTestUser.java")
        }
      }
    }
  }

  private fun File.createEntity(className: String, path: String) {
    this.resolve(path).apply { parentFile.mkdirs() }.writeText("""
      package com.example;

      import android.arch.persistence.room.Entity;

      @Entity
      public class $className {}
      """.trimIndent())
  }

  private fun doTest(path: String, vararg expected: String) {
    val daoName = Files.getNameWithoutExtension(path)
    val dao = projectRule.project.baseDir.writeChild(
        path,
        """
          package com.example;

          import android.arch.persistence.room.Dao;
          import android.arch.persistence.room.Query;
          import java.util.List;

          @Dao
          public interface $daoName {
            @Query("SELECT * FROM <caret>")
            List<User> getAll();
          }
          """.trimIndent())
    projectRule.fixture.configureFromExistingVirtualFile(dao)
    assertThat(projectRule.fixture.completeBasic().map { it.lookupString }).containsExactly(*expected)
  }

  @Test
  fun testMainScope() = doTest("app/src/main/java/com/example/UserDao.java", "User")
  @Test
  fun testTestScope() = doTest("app/src/test/java/com/example/TestUserDao.java", "User", "TestUser")
  @Test
  fun testAndroidTestScope() = doTest("app/src/androidTest/java/com/example/AndroidTestUserDao.java", "User", "AndroidTestUser")
}
