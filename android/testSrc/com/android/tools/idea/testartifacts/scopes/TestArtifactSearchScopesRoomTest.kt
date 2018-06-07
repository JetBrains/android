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

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.writeChild

class TestArtifactSearchScopesRoomTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    loadSimpleApplication()

    val appBuildDotGradle = project.baseDir.findFileByRelativePath("app/build.gradle")!!
    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.saveText(
          appBuildDotGradle,
          VfsUtil.loadText(appBuildDotGradle) +"\ndependencies { implementation \"android.arch.persistence.room:runtime:1.0.0\" }")

      createEntity(className = "User", path = "app/src/main/java/com/example/User.java")
      createEntity(className = "TestUser", path = "app/src/test/java/com/example/TestUser.java")
      createEntity(className = "AndroidTestUser", path = "app/src/androidTest/java/com/example/AndroidTestUser.java")
    }

    requestSyncAndWait()
  }

  private fun createEntity(className: String, path: String) {
    project.baseDir.writeChild(path, """
      package com.example;

      import android.arch.persistence.room.Entity;

      @Entity
      public class $className {}
      """.trimIndent())
  }

  private fun doTest(path: String, vararg expected: String) {
    val daoName = Files.getNameWithoutExtension(path)
    val dao = project.baseDir.writeChild(
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
    myFixture.configureFromExistingVirtualFile(dao)
    assertThat(myFixture.completeBasic().map { it.lookupString }).containsExactly(*expected)
  }

  fun testMainScope() = doTest("app/src/main/java/com/example/UserDao.java", "User")
  fun testTestScope() = doTest("app/src/test/java/com/example/TestUserDao.java", "User", "TestUser")
  fun testAndroidTestScope() = doTest("app/src/androidTest/java/com/example/AndroidTestUserDao.java", "User", "AndroidTestUser")
}
