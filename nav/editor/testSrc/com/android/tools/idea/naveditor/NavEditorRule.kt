/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.naveditor

import com.android.test.testutils.TestUtils
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.naveditor.scene.TestableThumbnailManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.TestProjectPaths.NAVIGATION_EDITOR_BASIC
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.io.ZipUtil
import org.jetbrains.android.AndroidTestBase
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import java.io.File
import java.nio.file.Paths

class NavEditorRule(
  private val projectRule: AndroidProjectRule? = null,
  private val projectName: String = NAVIGATION_EDITOR_BASIC
) : TestRule {

  val testDataPath: String
    get() = "${navEditorPluginHome}/testData"

  private val navEditorPluginHome: String
    get() {
      val adtPath = TestUtils.resolveWorkspacePath("tools/adt/idea/nav/editor").toString()
      return if (File(adtPath).exists()) adtPath else AndroidTestBase.getAndroidPluginHome()
    }

  private val myProjectRule = projectRule ?: AndroidProjectRule.withSdk()

  fun model(name: String, f: () -> ComponentDescriptor) = runInEdtAndGet { modelBuilder(name, f).build() }

  fun modelBuilder(name: String, f: () -> ComponentDescriptor): ModelBuilder =
    NavModelBuilderUtil.model(name, myProjectRule.module.androidFacet!!, myProjectRule.fixture as JavaCodeInsightTestFixture, f)

  override fun apply(statement: Statement, description: Description): Statement {
    var localStatement: Statement = object : Statement() {
      override fun evaluate() {
        val errors = mutableListOf<Throwable>()
        before()
        try {
          statement.evaluate()
        }
        catch (e: Throwable) {
          errors.add(e)
        }
        MultipleFailureException.assertEmpty(errors)
      }
    }
    if (projectRule == null) {
      localStatement = myProjectRule.apply(localStatement, description)
    }
    return localStatement
  }

  private fun before() {
    myProjectRule.fixture.testDataPath = AndroidTestBase.getTestDataPath()

    myProjectRule.fixture.copyDirectoryToProject("$projectName/app/src/main/java", "src")
    myProjectRule.fixture.copyDirectoryToProject("$projectName/app/src/main/res", "res")
    myProjectRule.fixture.copyFileToProject("$projectName/app/src/main/AndroidManifest.xml", "AndroidManifest.xml")

    for ((prebuilt, libName) in navEditorAarPaths.entries) {
      val tempDir = FileUtil.createTempDirectory("NavigationTest", null)
      val aar = File(prebuilt)
      ZipUtil.extract(aar, tempDir, null)
      val unzippedClasses = FileUtil.createTempDirectory("unzipClasses", null)
      ZipUtil.extract(File(tempDir, "classes.jar"), unzippedClasses, null)

      val virtualFileList = mutableListOf(VfsUtil.findFileByIoFile(unzippedClasses, true))
      val resFile = File(tempDir, "res")
      if (resFile.exists()) {
        virtualFileList.add(VfsUtil.findFileByIoFile(resFile, true))
      }

      PsiTestUtil.addProjectLibrary(myProjectRule.module, libName, virtualFileList, emptyList<VirtualFile>())
      VfsUtil.markDirtyAndRefresh(false, true, true, unzippedClasses)

      // TODO: support multiple modules

      myProjectRule.fixture.testDataPath = testDataPath
    }
    TestableThumbnailManager.register(myProjectRule.module.androidFacet!!)
  }
}

fun findVirtualProjectFile(project: Project, relativePath: String): VirtualFile? {
  val path = Paths.get(project.basePath!!, relativePath).normalize()
  return VfsUtil.findFileByIoFile(File(path.toString()), true)
}