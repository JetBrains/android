/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.google.common.truth.Expect
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AndroidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessorTest: UpgradeGradleFileModelTestCase() {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  private lateinit var mainManifestFile : VirtualFile
  private lateinit var debugManifestFile : VirtualFile

  @Before
  fun setUpManifestFiles() {
    runWriteAction {
      mainManifestFile = projectRule.fixture.tempDirFixture.createFile("src/main/AndroidManifest.xml")
      debugManifestFile = projectRule.fixture.tempDirFixture.createFile("src/debug/AndroidManifest.xml")
      Assert.assertTrue(mainManifestFile.isWritable)
      Assert.assertTrue(debugManifestFile.isWritable)
    }
  }

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("7.0.0" to "7.2.0") to AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE,
      ("7.1.0" to "8.0.0") to AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT,
      ("8.0.0" to "8.1.0") to AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT,
      ("8.0.0" to "9.0.0") to AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT,
      ("7.1.0" to "9.0.0") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
      ("9.0.0" to "9.1.0") to AgpUpgradeComponentNecessity.IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = AndroidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      expect.that(processor.necessity()).isEqualTo(u)
    }
  }

  @Test
  fun testReadMoreUrl() {
    val processor = AndroidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    Assert.assertEquals("https://developer.android.com/r/tools/upgrade-assistant/extract-native-libs-deprecated", processor.getReadMoreUrl())
  }

  @Test
  fun testExtractNativeLibsToUseLegacyPackaging() {
    writeToBuildFile(TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ExtractNativeLibsToUseLegacyPackaging"))
    writeToManifestFile(TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ManifestWithExtractNativeLibs"))
    val processor = AndroidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ExtractNativeLibsToUseLegacyPackagingExpected"))
    verifyManifestFileContents(mainManifestFile, TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ManifestWithoutExtractNativeLibs"))
  }

  @Test
  fun testExtractNativeLibsToUseLegacyPackagingNoValue() {
    writeToBuildFile(TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ExtractNativeLibsToUseLegacyPackaging"))
    writeToManifestFile(TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ManifestWithoutExtractNativeLibs"))
    val processor = AndroidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ExtractNativeLibsToUseLegacyPackaging"))
    verifyManifestFileContents(mainManifestFile, TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ManifestWithoutExtractNativeLibs"))
  }

  @Test
  fun testExtractNativeLibsToUseLegacyPackagingValueInDebugManifest() {
    writeToBuildFile(TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ExtractNativeLibsToUseLegacyPackaging"))
    writeToManifestFile(TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ManifestWithoutExtractNativeLibs"))
    writeToManifestFile(TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ManifestWithExtractNativeLibs"), debugManifestFile)
    val processor = AndroidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ExtractNativeLibsToUseLegacyPackaging"))
    verifyManifestFileContents(mainManifestFile, TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ManifestWithoutExtractNativeLibs"))
    // Only "main" manifest should be changed
    verifyManifestFileContents(debugManifestFile, TestFileName("AndroidManifestExtractNativeLibsToUseLegacyPackaging/ManifestWithExtractNativeLibs"))
  }

  private fun writeToManifestFile(fileName: TestFileName, file: VirtualFile = mainManifestFile) {
    val testFile = fileName.toFile(testDataPath, "")
    Assert.assertTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(file, VfsUtilCore.loadText(virtualTestFile!!)) }
  }

  private fun verifyManifestFileContents(file: VirtualFile, expected: TestFileName) {
    val expectedText = FileUtil.loadFile(expected.toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(file)
    Assert.assertEquals(expectedText, actualText)
  }
}