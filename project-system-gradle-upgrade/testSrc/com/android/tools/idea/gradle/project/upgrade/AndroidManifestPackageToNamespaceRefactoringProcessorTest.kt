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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.*
import com.google.common.truth.Expect
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AndroidManifestPackageToNamespaceRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  private lateinit var manifestFile : VirtualFile
  private lateinit var androidTestManifestFile : VirtualFile

  @Before
  fun setUpManifestFiles() {
    runWriteAction {
      manifestFile = projectRule.fixture.tempDirFixture.createFile("src/main/AndroidManifest.xml")
      androidTestManifestFile = projectRule.fixture.tempDirFixture.createFile("src/androidTest/AndroidManifest.xml")
      assertTrue(manifestFile.isWritable)
      assertTrue(androidTestManifestFile.isWritable)
    }
  }

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("4.0.0" to "4.2.0") to IRRELEVANT_FUTURE,
      ("4.1.0" to "7.0.0") to OPTIONAL_CODEPENDENT,
      ("7.0.0" to "7.1.0") to OPTIONAL_INDEPENDENT,
      ("7.0.0" to "8.0.0") to MANDATORY_INDEPENDENT,
      ("4.1.0" to "8.0.0") to MANDATORY_CODEPENDENT,
      ("8.0.0" to "8.1.0") to IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      expect.that(processor.necessity()).isEqualTo(u)
    }
  }

  @Test
  fun testReadMoreUrl() {
    val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("7.0.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/manifest-package-deprecated", processor.getReadMoreUrl())
  }

  @Test
  fun testPackageToNamespace() {
    writeToBuildFile(TestFileName("AndroidManifestPackageToNamespace/PackageToNamespace"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithPackage"))
    val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AndroidManifestPackageToNamespace/PackageToNamespaceExpected"))
    verifyManifestFileContents(manifestFile, TestFileName("AndroidManifestPackageToNamespace/ManifestWithoutPackage"))
  }

  @Test
  fun testPackageToConflictingNamespace() {
    writeToBuildFile(TestFileName("AndroidManifestPackageToNamespace/PackageToConflictingNamespace"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithPackage"))
    val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AndroidManifestPackageToNamespace/PackageToConflictingNamespaceExpected"))
    verifyManifestFileContents(manifestFile, TestFileName("AndroidManifestPackageToNamespace/ManifestWithoutPackage"))
  }

  @Test
  fun testAndroidTestPackageToDefaultTestNamespace() {
    writeToBuildFile(TestFileName("AndroidManifestPackageToNamespace/PackageToNamespace"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithPackage"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithTestPackage"), androidTestManifestFile)
    val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AndroidManifestPackageToNamespace/PackageToDefaultTestNamespaceExpected"))
    verifyManifestFileContents(manifestFile, TestFileName("AndroidManifestPackageToNamespace/ManifestWithoutPackage"))
    verifyManifestFileContents(androidTestManifestFile, TestFileName("AndroidManifestPackageToNamespace/ManifestWithoutPackage"))
  }

  @Test
  fun testAndroidTestPackageToDifferentTestNamespace() {
    writeToBuildFile(TestFileName("AndroidManifestPackageToNamespace/PackageToNamespace"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithPackage"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithDifferentTestPackage"), androidTestManifestFile)
    val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AndroidManifestPackageToNamespace/PackageToNamespaceWithDifferentTestNamespaceExpected"))
    verifyManifestFileContents(manifestFile, TestFileName("AndroidManifestPackageToNamespace/ManifestWithoutPackage"))
    verifyManifestFileContents(androidTestManifestFile, TestFileName("AndroidManifestPackageToNamespace/ManifestWithoutPackage"))
  }

  @Test
  fun testAndroidTestPackageToSameNamespaceIsBlocked() {
    writeToBuildFile(TestFileName("AndroidManifestPackageToNamespace/PackageToNamespace"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithPackage"))
    writeToManifestFile(TestFileName("AndroidManifestPackageToNamespace/ManifestWithPackage"), androidTestManifestFile)
    val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("7.0.0"))
    assertTrue(processor.isBlocked)
  }

  private fun writeToManifestFile(fileName: TestFileName, file: VirtualFile = manifestFile) {
    val testFile = fileName.toFile(testDataPath, "")
    assertTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(file, VfsUtilCore.loadText(virtualTestFile!!)) }
  }

  private fun verifyManifestFileContents(file: VirtualFile, expected: TestFileName) {
    val expectedText = FileUtil.loadFile(expected.toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(file)
    assertEquals(expectedText, actualText)
  }
}