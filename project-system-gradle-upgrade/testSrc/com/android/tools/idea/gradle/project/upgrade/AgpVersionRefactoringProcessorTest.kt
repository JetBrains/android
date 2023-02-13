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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.utils.FileUtils
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertSize
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@RunsInEdt
class AgpVersionRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  private lateinit var gradlePropertiesFile : VirtualFile
  private lateinit var versionCatalogFile : VirtualFile

  @Before
  fun setUpGradlePropertiesFile() {
    runWriteAction {
      gradlePropertiesFile = projectRule.fixture.tempDirFixture.createFile("gradle.properties")
      versionCatalogFile = projectRule.fixture.tempDirFixture.createFile(FileUtils.toSystemDependentPath("gradle/libs.versions.toml"))
      assertTrue(gradlePropertiesFile.isWritable)
      assertTrue(versionCatalogFile.isWritable)
    }
  }

  @Test
  fun testIsAlwaysEnabled() {
    val versions = listOf("1.5.0", "2.2.0", "2.3.2", "3.0.0", "3.3.2", "3.4.2", "3.5.0", "4.0.0", "4.1.0", "5.0.0", "5.1.0")
    versions.forEach { current ->
      versions.forEach { new ->
        val currentVersion = AgpVersion.parse(current)
        val newVersion = AgpVersion.parse(new)
        if (newVersion > currentVersion) {
          val processor = AgpVersionRefactoringProcessor(project, currentVersion, newVersion)
          assertTrue(processor.isEnabled)
        }
      }
    }
  }

  @Test
  fun testIsAlwaysMandatoryCodependent() {
    val versions = listOf("1.5.0", "2.2.0", "2.3.2", "3.0.0", "3.3.2", "3.4.2", "3.5.0", "4.0.0", "4.1.0", "5.0.0", "5.1.0")
    versions.forEach { current ->
      versions.forEach { new ->
        val currentVersion = AgpVersion.parse(current)
        val newVersion = AgpVersion.parse(new)
        if (newVersion > currentVersion) {
          val processor = AgpVersionRefactoringProcessor(project, currentVersion, newVersion)
          assertEquals(processor.necessity(), MANDATORY_CODEPENDENT)
        }
      }
    }
  }

  @Test
  fun testVersionInLiteral() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInLiteralExpected"))
  }

  @Test
  fun testNonsenseInLiteral() {
    writeToBuildFile(TestFileName("AgpVersion/NonsenseInLiteral"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/NonsenseInLiteral"))
  }

  @Test
  fun testVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInInterpolatedVariable"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInInterpolatedVariableExpected"))
  }

  @Test
  fun testNonsenseInInterpolatedVariable() {
    writeToBuildFile(TestFileName("AgpVersion/NonsenseInInterpolatedVariable"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/NonsenseInInterpolatedVariable"))
  }

  @Test
  fun testOverrideIsEnabled() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInInterpolatedVariable"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    assertTrue(processor.isEnabled)
    processor.isEnabled = false
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInInterpolatedVariable"))
  }

  @Test
  fun testIsNotNoOpOnVersionInLiteral() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    assertFalse(processor.isAlwaysNoOpForProject)
  }

  @Test
  fun testIsNotNoOpOnVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInInterpolatedVariable"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    assertFalse(processor.isAlwaysNoOpForProject)
  }

  @Test
  fun testNonClasspathDependencies() {
    writeToBuildFile(TestFileName("AgpVersion/BuildSrcDependency"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/BuildSrcDependency"))
  }

  @Test
  fun testDependenciesInBuildSrc() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    lateinit var buildSrcFile: VirtualFile
    runWriteAction {
      buildSrcFile = projectRule.fixture.tempDirFixture.findOrCreateDir("buildSrc").createChildData(this, buildFileName)
      val testFile = TestFileName("AgpVersion/BuildSrcDependency").toFile(testDataPath, testDataExtension!!)
      val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
      VfsUtil.saveText(buildSrcFile, VfsUtilCore.loadText(virtualTestFile!!))
    }
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInLiteralExpected"))
    verifyFileContents(buildSrcFile, TestFileName("AgpVersion/BuildSrcDependencyExpected"))
  }

  @Test
  fun testNonsenseInBuildSrc() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    lateinit var buildSrcFile: VirtualFile
    runWriteAction {
      buildSrcFile = projectRule.fixture.tempDirFixture.findOrCreateDir("buildSrc").createChildData(this, buildFileName)
      val testFile = TestFileName("AgpVersion/BuildSrcNonsense").toFile(testDataPath, testDataExtension!!)
      val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
      VfsUtil.saveText(buildSrcFile, VfsUtilCore.loadText(virtualTestFile!!))
    }
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInLiteralExpected"))
    verifyFileContents(buildSrcFile, TestFileName("AgpVersion/BuildSrcNonsense"))
  }

  @Test
  fun testPluginDslDependency() {
    writeToBuildFile(TestFileName("AgpVersion/PluginDslDependency"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/PluginDslDependencyExpected"))
  }

  @Test
  fun testPluginDslNonsense() {
    writeToBuildFile(TestFileName("AgpVersion/PluginDslNonsense"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/PluginDslNonsense"))
  }

  @Test
  fun testPluginManagementDependency() {
    writeToSettingsFile(TestFileName("AgpVersion/PluginManagementDependency"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(settingsFile, TestFileName("AgpVersion/PluginManagementDependencyExpected"))
  }

  @Test
  fun testPluginManagementVariableDependency() {
    writeToSettingsFile(TestFileName("AgpVersion/PluginManagementVariableDependency"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(settingsFile, TestFileName("AgpVersion/PluginManagementVariableDependencyExpected"))
  }

  @Test
  fun testPluginManagementInterpolationDependency() {
    writeToSettingsFile(TestFileName("AgpVersion/PluginManagementInterpolationDependency"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(settingsFile, TestFileName("AgpVersion/PluginManagementInterpolationDependencyExpected"))
  }

  @Test
  fun testPluginsBlockInSettings() {
    writeToSettingsFile(TestFileName("AgpVersion/SettingsPlugin"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(settingsFile, TestFileName("AgpVersion/SettingsPluginExpected"))
  }

  @Test
  fun testPre80MavenPublishDoesNotBlockPre80Upgrades() {
    writeToBuildFile(TestFileName("AgpVersion/Pre80MavenPublish"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("7.2.0"))
    assertFalse(processor.isBlocked)
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/Pre80MavenPublish720Expected"))
  }

  @Test
  fun testPre80MavenPublishBlocks80Upgrades() {
    writeToBuildFile(TestFileName("AgpVersion/Pre80MavenPublish"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("8.0.0"))
    assertTrue(processor.isBlocked)
    assertSize(1, processor.blockProcessorReasons())
    assertEquals("Use of implicitly-created components in maven-publish.", processor.blockProcessorReasons()[0].shortDescription)
  }

  @Test
  fun testPre80MavenPublishOptOutBlocks() {
    writeToBuildFile(TestFileName("AgpVersion/Pre80MavenPublish"))
    writeToGradlePropertiesFile("android.disableAutomaticComponentCreation=false\n")
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("8.0.0"))
    assertTrue(processor.isBlocked)
    assertSize(1, processor.blockProcessorReasons())
    assertEquals("Use of implicitly-created components in maven-publish.", processor.blockProcessorReasons()[0].shortDescription)
  }

  @Test
  fun testPre80MavenPublishOptOutCaseBlocks() {
    writeToBuildFile(TestFileName("AgpVersion/Pre80MavenPublish"))
    writeToGradlePropertiesFile("android.disableAutomaticComponentCreation=FaLsE\n")
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("8.0.0"))
    assertTrue(processor.isBlocked)
    assertSize(1, processor.blockProcessorReasons())
    assertEquals("Use of implicitly-created components in maven-publish.", processor.blockProcessorReasons()[0].shortDescription)
  }

  @Test
  fun testPre80MavenPublishOptInDoesNotBlock() {
    writeToBuildFile(TestFileName("AgpVersion/Pre80MavenPublish"))
    writeToGradlePropertiesFile("android.disableAutomaticComponentCreation=true\n")
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("8.0.0"))
    assertFalse(processor.isBlocked)
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/Pre80MavenPublish800Expected"))
  }

  @Test
  fun testPre80MavenPublishOptInCaseDoesNotBlock() {
    writeToBuildFile(TestFileName("AgpVersion/Pre80MavenPublish"))
    writeToGradlePropertiesFile("android.disableAutomaticComponentCreation=TrUe\n")
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("8.0.0"))
    assertFalse(processor.isBlocked)
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/Pre80MavenPublish800Expected"))
  }

  @Test
  fun testPre80MavenPublishCollidingPropertyNameBlocks() {
    writeToBuildFile(TestFileName("AgpVersion/Pre80MavenPublish"))
    writeToGradlePropertiesFile("notandroid.disableAutomaticComponentCreation=true\n")
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("8.0.0"))
    assertTrue(processor.isBlocked)
    assertSize(1, processor.blockProcessorReasons())
    assertEquals("Use of implicitly-created components in maven-publish.", processor.blockProcessorReasons()[0].shortDescription)
  }

  @Test
  fun testVersionInLiteral80() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("8.0.0"))
    assertFalse(processor.isBlocked)
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInLiteral80Expected"))
  }

  @Test
  fun testVersionInVersionCatalogLiteral() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFileName("AgpVersion/VersionInVersionCatalog"))
      writeToVersionCatalogFile(TestFileName("AgpVersion/VersionCatalogLiteral"))
      val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.2.0"), AgpVersion.parse("8.0.0"))
      assertFalse(processor.isBlocked)
      processor.run()
      verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInVersionCatalog"))
      verifyVersionCatalogFileContents(versionCatalogFile, TestFileName("AgpVersion/VersionCatalogLiteralExpected"))
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionInVersionCatalogMap() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFileName("AgpVersion/VersionInVersionCatalog"))
      writeToVersionCatalogFile(TestFileName("AgpVersion/VersionCatalogMap"))
      val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.2.0"), AgpVersion.parse("8.0.0"))
      assertFalse(processor.isBlocked)
      processor.run()
      verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInVersionCatalog"))
      verifyVersionCatalogFileContents(versionCatalogFile, TestFileName("AgpVersion/VersionCatalogMapExpected"))
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testNonsenseInVersionCatalog () {
    writeToBuildFile(TestFileName("AgpVersion/VersionInVersionCatalog"))
    writeToVersionCatalogFile(TestFileName("AgpVersion/VersionCatalogNonsense"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.2.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInVersionCatalog"))
    verifyVersionCatalogFileContents(versionCatalogFile, TestFileName("AgpVersion/VersionCatalogNonsense"))
  }

  @Test
  fun testVersionInVersionCatalogVersionRef() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {

      writeToBuildFile(TestFileName("AgpVersion/VersionInVersionCatalog"))
      writeToVersionCatalogFile(TestFileName("AgpVersion/VersionCatalogVersionRef"))
      val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.2.0"), AgpVersion.parse("8.0.0"))
      assertFalse(processor.isBlocked)
      processor.run()
      verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInVersionCatalog"))
      verifyVersionCatalogFileContents(versionCatalogFile, TestFileName("AgpVersion/VersionCatalogVersionRefExpected"))
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLiteralTooltipsNotNull() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    val usages = processor.findUsages()
    assertTrue(usages.isNotEmpty())
    usages.forEach { assertNotNull(it.tooltipText) }
  }

  @Test
  fun testInterpolatedVariableTooltipsNotNull() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInInterpolatedVariable"))
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    val usages = processor.findUsages()
    assertTrue(usages.isNotEmpty())
    usages.forEach { assertNotNull(it.tooltipText) }
  }

  @Test
  fun testUncompressedNativeLibsDisabledBlocked() {
    writeToBuildFile(TestFileName("AgpVersion/UncompressedNativeLibsDisabled"))
    writeToGradlePropertiesFile("android.bundle.enableUncompressedNativeLibs=false\n")
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("8.1.0"))
    assertTrue(processor.isBlocked)
    assertSize(1, processor.blockProcessorReasons())
    assertEquals(
      "Uncompressed native libs in bundle is a deprecated property.",
      processor.blockProcessorReasons()[0].shortDescription
    )
  }

  @Test
  fun testUncompressedNativeLibsDisabledTrueDoesNotBlock() {
    writeToBuildFile(TestFileName("AgpVersion/UncompressedNativeLibsDisabled"))
    writeToGradlePropertiesFile("android.bundle.enableUncompressedNativeLibs=true\n")
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("8.1.0"))
    assertFalse(processor.isBlocked)
  }

  @Test
  fun testUncompressedNativeLibsDisabledFalseDoesNotBlockVersionNotReached() {
    writeToBuildFile(TestFileName("AgpVersion/UncompressedNativeLibsDisabled"))
    writeToGradlePropertiesFile("android.bundle.enableUncompressedNativeLibs=false\n")
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("7.1.0"), AgpVersion.parse("8.0.0"))
    assertFalse(processor.isBlocked)
  }

  @Test
  fun testUncompressedNativeLibsDisabledFalseDoesNotBlockVersionAlreadyReached() {
    writeToBuildFile(TestFileName("AgpVersion/UncompressedNativeLibsDisabled"))
    writeToGradlePropertiesFile("android.bundle.enableUncompressedNativeLibs=false\n")
    val processor = AgpVersionRefactoringProcessor(project, AgpVersion.parse("8.1.0"), AgpVersion.parse("8.1.0"))
    assertFalse(processor.isBlocked)
  }

  private fun writeToGradlePropertiesFile(text: String) {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, text) }
  }

  private fun writeToVersionCatalogFile(fileName: TestFileName) {
    val testFile = fileName.toFile(testDataPath, ".toml")
    assertTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(versionCatalogFile, VfsUtilCore.loadText(virtualTestFile!!)) }
  }

  @Throws(IOException::class)
  private fun verifyVersionCatalogFileContents(file: VirtualFile, expected: TestFileName) {
    fun String.normalize() = replace("[ \\t]+".toRegex(), "").trim { it <= ' ' }

    val expectedText = FileUtil.loadFile(expected.toFile(testDataPath, ".toml")).normalize()
    val actualText = VfsUtilCore.loadText(file).normalize()
    Assert.assertEquals(expectedText, actualText)
  }
}