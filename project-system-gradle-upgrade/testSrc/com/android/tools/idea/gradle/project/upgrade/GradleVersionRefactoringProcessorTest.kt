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

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@RunsInEdt
class GradleVersionRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  private lateinit var wrapperSettingsFile : VirtualFile

  @Before
  fun setUpWrapperSettingsFile() {
    runWriteAction {
      wrapperSettingsFile = projectRule.fixture.tempDirFixture.createFile("gradle/wrapper/gradle-wrapper.properties")
      assertTrue(wrapperSettingsFile.isWritable)
    }
  }

  @Test
  fun testIsAlwaysEnabled() {
    val versions = listOf("1.5.0", "2.2.0", "2.3.2", "3.0.0", "3.3.2", "3.4.2", "3.5.0", "4.0.0", "4.1.0", "5.0.0", "5.1.0")
    versions.forEach { current ->
      versions.forEach { new ->
        val currentVersion = GradleVersion.parse(current)
        val newVersion = GradleVersion.parse(new)
        if (newVersion > currentVersion) {
          val processor = GradleVersionRefactoringProcessor(project, currentVersion, newVersion)
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
        val currentVersion = GradleVersion.parse(current)
        val newVersion = GradleVersion.parse(new)
        if (newVersion > currentVersion) {
          val processor = GradleVersionRefactoringProcessor(project, currentVersion, newVersion)
          assertEquals(processor.necessity(), MANDATORY_CODEPENDENT)
        }
      }
    }
  }

  // Although the AgpGradleVersionRefactoringProcessor is part of the GradleBuildModel set of RefactoringProcessors, it
  // in fact edits a file (the Gradle Wrapper properties file) which is not strictly part of the build model.  That means it can't
  // be unit tested in quite the same way.

  private fun writeToGradleWrapperPropertiesFile(fileName: TestFileName) {
    val testFile = fileName.toFile(testDataPath, "")
    assertTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(wrapperSettingsFile, VfsUtilCore.loadText(virtualTestFile!!)) }
  }

  @Test
  fun testOldGradleVersion360() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("3.6.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersion360Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersion400() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersion400Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersion410() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersion410Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersion420() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.2.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersion420Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionAll() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersionAll"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersionAllExpected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionFile() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersionFile"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersionFileExpected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionFileAll() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersionFileAll"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersionFileAllExpected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionEscaped() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersionEscaped"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersion410Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionFileEscaped() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersionFile"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersionFileExpected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testRCGradleVersionEscaped() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/RCGradleVersionEscaped"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersion410Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOverrideIsEnabled() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    assertTrue(processor.isEnabled)
    processor.isEnabled = false
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("GradleVersion/OldGradleVersion").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testTooltipsNotNull() {
    writeToGradleWrapperPropertiesFile(TestFileName("GradleVersion/OldGradleVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    val usages = processor.findUsages()
    assertTrue(usages.isNotEmpty())
    usages.forEach { assertNotNull(it.tooltipText) }
  }

  @Test
  fun testKotlinPluginVersionInLiteral() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginVersionInLiteral"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginVersionInLiteralExpected"))
  }

  @Test
  fun testKotlinPluginVersionInDsl() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginVersionInDsl"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginVersionInDslExpected"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInLiteral() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginNewEnoughVersionInLiteral"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginNewEnoughVersionInLiteral"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInDsl() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginNewEnoughVersionInDsl"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginNewEnoughVersionInDsl"))
  }

  @Test
  fun testKotlinPluginVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginVersionInInterpolatedVariable"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginVersionInInterpolatedVariableExpected"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginNewEnoughVersionInInterpolatedVariable"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginNewEnoughVersionInInterpolatedVariable"))
  }

  @Test
  fun testKotlinPluginVersionPlus() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginVersionPlus"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginVersionPlus"))
  }

  @Test
  fun testKotlinPluginUnknownVersion() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginUnknownVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginUnknownVersion"))
  }

  @Test
  fun testKotlinPluginVersionInLiteral70() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginVersionInLiteral"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginVersionInLiteral70Expected"))
  }

  @Test
  fun testKotlinPluginVersionInDsl70() {
    writeToBuildFile(TestFileName("GradleVersion/KotlinPluginVersionInDsl"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/KotlinPluginVersionInDsl70Expected"))
  }

  @Test
  fun testSafeArgsVersionInLiteral() {
    writeToBuildFile(TestFileName("GradleVersion/SafeArgsVersionInLiteral"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/SafeArgsVersionInLiteral"))
  }

  @Test
  fun testSafeArgsVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("GradleVersion/SafeArgsVersionInInterpolatedVariable"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/SafeArgsVersionInInterpolatedVariable"))
  }

  @Test
  fun testSafeArgsVersionInDsl() {
    writeToBuildFile(TestFileName("GradleVersion/SafeArgsVersionInDsl"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/SafeArgsVersionInDsl"))
  }

  @Test
  fun testAndroidJUnit5VersionTo400() {
    writeToBuildFile(TestFileName("GradleVersion/AndroidJUnit5Version"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.6.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/AndroidJUnit5VersionTo400Expected"))
  }

  @Test
  fun testAndroidJUnit5VersionTo410() {
    writeToBuildFile(TestFileName("GradleVersion/AndroidJUnit5Version"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.6.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/AndroidJUnit5VersionTo410Expected"))
  }

  @Test
  fun testAndroidJUnit5VersionInDslTo410() {
    writeToBuildFile(TestFileName("GradleVersion/AndroidJUnit5VersionInDsl"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.6.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/AndroidJUnit5VersionInDslTo410Expected"))
  }

  @Test
  fun testFirebaseCrashlyticsVersionTo420() {
    writeToBuildFile(TestFileName("GradleVersion/FirebaseCrashlyticsVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/FirebaseCrashlyticsVersionTo420Expected"))
  }

  @Test
  fun testFirebaseCrashlyticsVersionTo700() {
    writeToBuildFile(TestFileName("GradleVersion/FirebaseCrashlyticsVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/FirebaseCrashlyticsVersionTo700Expected"))
  }

  @Test
  fun testFirebaseCrashlyticsVersionInDslTo700() {
    writeToBuildFile(TestFileName("GradleVersion/FirebaseCrashlyticsVersionInDsl"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/FirebaseCrashlyticsVersionInDslTo700Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionTo400() {
    writeToBuildFile(TestFileName("GradleVersion/FirebaseAppdistributionVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/FirebaseAppdistributionVersionTo400Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionTo700() {
    writeToBuildFile(TestFileName("GradleVersion/FirebaseAppdistributionVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/FirebaseAppdistributionVersionTo700Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionInDslTo400() {
    writeToBuildFile(TestFileName("GradleVersion/FirebaseAppdistributionVersionInDsl"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/FirebaseAppdistributionVersionInDslTo400Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionInDslTo700() {
    writeToBuildFile(TestFileName("GradleVersion/FirebaseAppdistributionVersionInDsl"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/FirebaseAppdistributionVersionInDslTo700Expected"))
  }

  @Test
  fun testGoogleOssLicensesVersionTo700() {
    writeToBuildFile(TestFileName("GradleVersion/GoogleOssLicensesVersion"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/GoogleOssLicensesVersionTo700Expected"))
  }

  @Test
  fun testGoogleOssLicensesVersionInDslTo700() {
    writeToBuildFile(TestFileName("GradleVersion/GoogleOssLicensesVersionInDsl"))
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("GradleVersion/GoogleOssLicensesVersionInDslTo700Expected"))
  }

  // TODO(b/159420573): test that with a sufficiently new (>= GRADLE_MINIMUM_VERSION) declared version of gradle, this
  //  processor does nothing.  (Need to programmatically write the properties file so that it doesn't fail when
  //  GRADLE_MINIMUM_VERSION changes)
}
