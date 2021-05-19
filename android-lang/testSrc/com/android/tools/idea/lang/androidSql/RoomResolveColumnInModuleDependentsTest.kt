/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.lang.getTestDataPath
import com.google.common.truth.Truth
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import java.io.File

class RoomResolveColumnInModuleDependentsTest : UsefulTestCase() {

  private lateinit var myFixture: JavaCodeInsightTestFixture

  private lateinit var libModule: Module
  private lateinit var appModule: Module

  /**
   * Set up with to modules where mySecondModule depends on myFirstModule.
   */
  override fun setUp() {
    super.setUp()

    val projectBuilder = JavaTestFixtureFactory.createFixtureBuilder(name)
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
    myFixture.testDataPath = getTestDataPath()

    val libModuleFixture: ModuleFixture = newModule(projectBuilder, "lib")
    val appModuleFixture: ModuleFixture = newModule(projectBuilder, "app")

    myFixture.setUp()

    libModule = libModuleFixture.module
    appModule = appModuleFixture.module

    ModuleRootModificationUtil.addDependency(appModule, libModule)
  }

  override fun tearDown() {
    try {
      myFixture.tearDown()
    } catch (t: Throwable) {
      addSuppressedException(t)
    } finally {
      super.tearDown()
    }
  }

  private fun newModule(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>, contentRoot: String): ModuleFixture {
    val firstProjectBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder::class.java)
    val tempDirPath = myFixture.tempDirPath

    // Create a new content root for each module, and create a directory for it manually
    val contentRootPath = "$tempDirPath/$contentRoot"
    File(contentRootPath).mkdir()

    // Call the builder
    return firstProjectBuilder
      .addContentRoot(contentRootPath)
      .addSourceRoot("src")
      .fixture
  }

  fun testRename_dependentModule() {
    createStubRoomClassesInPath(myFixture, "lib/src")

    myFixture.addFileToProject("lib/src/User.java",
                               """
      import androidx.room.Entity;

      @Entity
      public class User {
        private int id;
      }
      """
    )
    val dao = myFixture.addFileToProject("app/src/Dao.java", """
        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT i<caret>d FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(dao.virtualFile)

    myFixture.renameElementAtCaret("user_id")

    myFixture.checkResult("""
        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT user_id FROM User") List<Integer> getIds();
        }
    """.trimIndent())

    Truth.assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findField("User", "user_id"))
  }

}