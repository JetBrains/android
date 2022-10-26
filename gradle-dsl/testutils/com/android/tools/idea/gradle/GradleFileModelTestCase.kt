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
package com.android.tools.idea.gradle

import com.android.SdkConstants
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.testing.AndroidProjectRule.Companion.onDisk
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import org.jetbrains.annotations.Contract
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.IOException

@Ignore // Needs to be ignored so bazel doesn't try to run this class as a test and fail with "No tests found".
@RunWith(Parameterized::class)
open class GradleFileModelTestCase {
  @get:Rule
  val nameRule = TestName()
  protected open val projectRule = onDisk()

  @get:Rule
  val ruleChain by lazy { RuleChain.outerRule(projectRule).around(EdtRule()) }

  @Parameterized.Parameter(0)
  @JvmField
  var testDataExtension: String? = null

  @Parameterized.Parameter(1)
  @JvmField
  var languageName: String? = null
  protected lateinit var buildFile: VirtualFile
  protected lateinit var settingsFile: VirtualFile
  protected lateinit var testDataPath: String
  private val isGroovy: Boolean get() = languageName == GROOVY_LANGUAGE
  protected val buildFileName: String get() = if (isGroovy) SdkConstants.FN_BUILD_GRADLE else SdkConstants.FN_BUILD_GRADLE_KTS
  protected val settingsFileName: String get() = if (isGroovy) SdkConstants.FN_SETTINGS_GRADLE else SdkConstants.FN_SETTINGS_GRADLE_KTS
  protected val gradleBuildModel: GradleBuildModel
    get() {
      val projectBuildModel = ProjectBuildModel.get(projectRule.project)
      val buildModel = projectBuildModel.getModuleBuildModel(projectRule.module)
      Assert.assertNotNull(buildModel)
      return buildModel!!
    }
  protected val project: Project get() = projectRule.project

  data class TestFileName(val path: String) {
    fun toFile(testDataPath: String, testDataExtension: String): File {
      val path = FileUtil.toSystemDependentName(testDataPath) + File.separator + FileUtil.toSystemDependentName(path) + testDataExtension
      return File(path)
    }
  }

  @Before
  fun setUp() {
    runWriteAction {
      buildFile = projectRule.fixture.tempDirFixture.createFile(buildFileName)
      settingsFile = projectRule.fixture.tempDirFixture.createFile(settingsFileName)
      assertTrue(buildFile.isWritable)
      assertTrue(settingsFile.isWritable)
    }
  }

  private fun writeToGradleFile(fileName: TestFileName, file: VirtualFile) {
    val testFile = fileName.toFile(testDataPath, testDataExtension!!)
    assertTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(file, VfsUtilCore.loadText(virtualTestFile!!)) }
  }

  protected fun writeToBuildFile(fileName: TestFileName) = writeToGradleFile(fileName, buildFile)

  protected fun writeToSettingsFile(fileName: TestFileName) = writeToGradleFile(fileName, settingsFile)

  protected fun applyChanges(buildModel: GradleBuildModel) {
    WriteCommandAction.runWriteCommandAction(projectRule.project) { buildModel.applyChanges() }
    Assert.assertFalse(buildModel.isModified)
  }

  protected fun applyChangesAndReparse(buildModel: GradleBuildModel) {
    applyChanges(buildModel)
    buildModel.reparse()
  }

  @Throws(IOException::class)
  protected fun verifyFileContents(file: VirtualFile, expected: TestFileName) {
    fun String.normalize() = replace("[ \\t]+".toRegex(), "").trim { it <= ' ' }

    val expectedText = FileUtil.loadFile(expected.toFile(testDataPath, testDataExtension!!)).normalize()
    val actualText = VfsUtilCore.loadText(file).normalize()
    Assert.assertEquals(expectedText, actualText)
  }

  companion object {
    private const val GROOVY_LANGUAGE = "Groovy"
    private const val KOTLIN_LANGUAGE = "Kotlin"

    @Contract(pure = true)
    @Parameterized.Parameters(name = "{1}")
    @JvmStatic
    fun languageExtensions(): Collection<*> {
      return listOf(
        arrayOf<Any>(".gradle", GROOVY_LANGUAGE),
        arrayOf<Any>(".gradle.kts", KOTLIN_LANGUAGE)
      )
    }
  }
}