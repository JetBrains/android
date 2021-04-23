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
class AgpGradleVersionRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
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
          val processor = AgpGradleVersionRefactoringProcessor(project, currentVersion, newVersion)
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
          val processor = AgpGradleVersionRefactoringProcessor(project, currentVersion, newVersion)
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
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("3.6.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersion360Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersion400() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersion400Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersion410() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersion410Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersion420() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.2.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersion420Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionAll() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersionAll"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersionAllExpected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionFile() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersionFile"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersionFileExpected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionFileAll() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersionFileAll"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersionFileAllExpected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionEscaped() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersionEscaped"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersion410Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOldGradleVersionFileEscaped() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersionFile"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersionFileExpected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testRCGradleVersionEscaped() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/RCGradleVersionEscaped"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersion410Expected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOverrideIsEnabled() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    assertTrue(processor.isEnabled)
    processor.isEnabled = false
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersion").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testTooltipsNotNull() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    val usages = processor.findUsages()
    assertTrue(usages.isNotEmpty())
    usages.forEach { assertNotNull(it.tooltipText) }
  }

  @Test
  fun testKotlinPluginVersionInLiteral() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginVersionInLiteral"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginVersionInLiteralExpected"))
  }

  @Test
  fun testKotlinPluginVersionInDsl() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginVersionInDsl"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginVersionInDslExpected"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInLiteral() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginNewEnoughVersionInLiteral"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginNewEnoughVersionInLiteral"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInDsl() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginNewEnoughVersionInDsl"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginNewEnoughVersionInDsl"))
  }

  @Test
  fun testKotlinPluginVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginVersionInInterpolatedVariable"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginVersionInInterpolatedVariableExpected"))
  }

  @Test
  fun testKotlinPluginNewEnoughVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginNewEnoughVersionInInterpolatedVariable"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginNewEnoughVersionInInterpolatedVariable"))
  }

  @Test
  fun testKotlinPluginVersionPlus() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginVersionPlus"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginVersionPlus"))
  }

  @Test
  fun testKotlinPluginUnknownVersion() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginUnknownVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginUnknownVersion"))
  }

  @Test
  fun testKotlinPluginVersionInLiteral70() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginVersionInLiteral"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginVersionInLiteral70Expected"))
  }

  @Test
  fun testKotlinPluginVersionInDsl70() {
    writeToBuildFile(TestFileName("AgpGradleVersion/KotlinPluginVersionInDsl"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/KotlinPluginVersionInDsl70Expected"))
  }

  @Test
  fun testSafeArgsVersionInLiteral() {
    writeToBuildFile(TestFileName("AgpGradleVersion/SafeArgsVersionInLiteral"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/SafeArgsVersionInLiteral"))
  }

  @Test
  fun testSafeArgsVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("AgpGradleVersion/SafeArgsVersionInInterpolatedVariable"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/SafeArgsVersionInInterpolatedVariable"))
  }

  @Test
  fun testSafeArgsVersionInDsl() {
    writeToBuildFile(TestFileName("AgpGradleVersion/SafeArgsVersionInDsl"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/SafeArgsVersionInDsl"))
  }

  @Test
  fun testAndroidJUnit5VersionTo400() {
    writeToBuildFile(TestFileName("AgpGradleVersion/AndroidJUnit5Version"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.6.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/AndroidJUnit5VersionTo400Expected"))
  }

  @Test
  fun testAndroidJUnit5VersionTo410() {
    writeToBuildFile(TestFileName("AgpGradleVersion/AndroidJUnit5Version"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.6.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/AndroidJUnit5VersionTo410Expected"))
  }

  @Test
  fun testAndroidJUnit5VersionInDslTo410() {
    writeToBuildFile(TestFileName("AgpGradleVersion/AndroidJUnit5VersionInDsl"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.6.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/AndroidJUnit5VersionInDslTo410Expected"))
  }

  @Test
  fun testFirebaseCrashlyticsVersionTo420() {
    writeToBuildFile(TestFileName("AgpGradleVersion/FirebaseCrashlyticsVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/FirebaseCrashlyticsVersionTo420Expected"))
  }

  @Test
  fun testFirebaseCrashlyticsVersionTo700() {
    writeToBuildFile(TestFileName("AgpGradleVersion/FirebaseCrashlyticsVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/FirebaseCrashlyticsVersionTo700Expected"))
  }

  @Test
  fun testFirebaseCrashlyticsVersionInDslTo700() {
    writeToBuildFile(TestFileName("AgpGradleVersion/FirebaseCrashlyticsVersionInDsl"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/FirebaseCrashlyticsVersionInDslTo700Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionTo400() {
    writeToBuildFile(TestFileName("AgpGradleVersion/FirebaseAppdistributionVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/FirebaseAppdistributionVersionTo400Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionTo700() {
    writeToBuildFile(TestFileName("AgpGradleVersion/FirebaseAppdistributionVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/FirebaseAppdistributionVersionTo700Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionInDslTo400() {
    writeToBuildFile(TestFileName("AgpGradleVersion/FirebaseAppdistributionVersionInDsl"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/FirebaseAppdistributionVersionInDslTo400Expected"))
  }

  @Test
  fun testFirebaseAppdistributionVersionInDslTo700() {
    writeToBuildFile(TestFileName("AgpGradleVersion/FirebaseAppdistributionVersionInDsl"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/FirebaseAppdistributionVersionInDslTo700Expected"))
  }

  @Test
  fun testGoogleOssLicensesVersionTo700() {
    writeToBuildFile(TestFileName("AgpGradleVersion/GoogleOssLicensesVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/GoogleOssLicensesVersionTo700Expected"))
  }

  @Test
  fun testGoogleOssLicensesVersionInDslTo700() {
    writeToBuildFile(TestFileName("AgpGradleVersion/GoogleOssLicensesVersionInDsl"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("AgpGradleVersion/GoogleOssLicensesVersionInDslTo700Expected"))
  }

  // TODO(b/159420573): test that with a sufficiently new (>= GRADLE_MINIMUM_VERSION) declared version of gradle, this
  //  processor does nothing.  (Need to programmatically write the properties file so that it doesn't fail when
  //  GRADLE_MINIMUM_VERSION changes)
}
