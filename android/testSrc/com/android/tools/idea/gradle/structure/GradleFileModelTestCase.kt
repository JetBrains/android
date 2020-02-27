/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.testing.AndroidProjectRule.Companion.onDisk
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.annotations.Contract
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.IOException

@Ignore // Needs to be ignored so bazel doesn't try to run this class as a test and fail with "No tests found".
@RunWith(Parameterized::class)
abstract class GradleFileModelTestCase {
  @get:Rule
  val nameRule = TestName()
  private val projectRule = onDisk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Parameterized.Parameter(0)
  @JvmField
  var testDataExtension: String? = null

  @Parameterized.Parameter(1)
  @JvmField
  var languageName: String? = null

  protected lateinit var buildFile: VirtualFile
  private lateinit var testDataPath: String

  private val isGroovy: Boolean get() = languageName == GROOVY_LANGUAGE
  private val buildFileName: String get() = if (isGroovy) SdkConstants.FN_BUILD_GRADLE else SdkConstants.FN_BUILD_GRADLE_KTS

  @Before
  fun setUp() {
    StudioFlags.KOTLIN_DSL_PARSING.override(true)
    runWriteAction {
      buildFile = projectRule.fixture.tempDirFixture.createFile(buildFileName)
      Assume.assumeTrue(buildFile.isWritable)
    }
    testDataPath = AndroidTestBase.getTestDataPath() + "/parser"
  }

  @After
  fun tearDown() {
    StudioFlags.KOTLIN_DSL_PARSING.clearOverride()
  }

  protected fun writeToBuildFile(fileName: TestFileName) {
    val testFile = fileName.toFile(testDataPath, testDataExtension!!)
    Assume.assumeTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(buildFile, VfsUtilCore.loadText(virtualTestFile!!)) }
  }

  protected val gradleBuildModel: GradleBuildModel
    get() {
      val projectBuildModel = ProjectBuildModel.get(projectRule.project)
      val buildModel = projectBuildModel.getModuleBuildModel(projectRule.module)
      Assert.assertNotNull(buildModel)
      return buildModel!!
    }

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
