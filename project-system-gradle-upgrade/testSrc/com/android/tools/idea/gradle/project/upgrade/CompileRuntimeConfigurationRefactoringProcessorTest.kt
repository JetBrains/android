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

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.*
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunsInEdt
class CompileRuntimeConfigurationRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testIsDisabledForUpgradeToOldAgp() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("2.3.2"), AgpVersion.parse("3.0.0"))
    assertFalse(processor.isEnabled)
  }

  @Test
  fun testIsEnabledForUpgradeToAgp35() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("2.3.2"), AgpVersion.parse("3.5.0"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsEnabledForUpgradeToAgp4() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.2.0"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsEnabledForUpgradeToAgp7() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsDisabledForUpgradeFromAgp7() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("7.1.0"))
    assertFalse(processor.isEnabled)
  }

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("2.3.2" to "3.0.0") to IRRELEVANT_FUTURE,
      ("2.3.2" to "3.5.0") to OPTIONAL_CODEPENDENT,
      ("3.1.0" to "3.6.0") to OPTIONAL_INDEPENDENT,
      ("3.6.0" to "7.1.0") to MANDATORY_INDEPENDENT,
      ("2.3.2" to "7.1.0") to MANDATORY_CODEPENDENT,
      ("7.0.0" to "7.1.0") to IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      assertEquals(u, processor.necessity())
    }
  }

  @Test
  fun testReadMoreUrl() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/compile-runtime-configuration", processor.getReadMoreUrl())
  }

  @Test
  fun testSimpleApplication() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleApplication"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleApplicationExpected"))
  }

  @Test
  fun testSimpleApplicationWithVersion() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleApplicationWithVersion"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleApplicationWithVersionExpected"))
  }

  @Test
  fun testApplicationWithDynamicFeatures() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/ApplicationWithDynamicFeatures"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/ApplicationWithDynamicFeaturesExpected"))
  }

  @Test
  fun testSimpleDynamicFeature() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleDynamicFeature"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleDynamicFeatureExpected"))
  }

  @Test
  fun testSimpleLibrary() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleLibrary"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleLibraryExpected"))
  }

  @Test
  fun testMapNotationDependency() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/MapNotationDependency"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/MapNotationDependencyExpected"))
  }

  @Test
  fun testSimpleJavaApplication() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleJavaApplication"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleJavaApplicationExpected"))
  }

  @Test
  fun testSimpleJavaLibrary() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleJavaLibrary"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleJavaLibraryExpected"))
  }

  @Test
  fun testSimpleOrgGradleJavaApplication() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleOrgGradleJavaApplication"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleOrgGradleJavaApplicationExpected"))
  }

  @Test
  fun testSimpleOrgGradleJavaLibrary() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleOrgGradleJavaLibrary"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleOrgGradleJavaLibraryExpected"))
  }

  @Test
  fun testUnknownPlugin() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/UnknownPlugin"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/UnknownPlugin"))
  }

  @Test
  fun testApplicationWith2DVariant() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/ApplicationWith2DVariant"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/ApplicationWith2DVariantExpected"))
  }

  @Test
  fun testBuildscriptDependenciesLeftAlone() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/BuildscriptDependenciesLeftAlone"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/BuildscriptDependenciesLeftAloneExpected"))
  }

  @Test
  fun testMoreObscureConfigurations() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/MoreObscureConfigurations"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/MoreObscureConfigurationsExpected"))
  }

  @Test
  fun testTestApiConfigurations() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/TestApiConfigurations"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/TestApiConfigurationsExpected"))
  }

  @Test
  fun testSimpleBasePlugin() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleBasePlugin"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleBasePluginExpected"))
  }

  @Test
  fun testSimpleAndroidPlugin() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleAndroidPlugin"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleAndroidPluginExpected"))

  }

  @Test
  fun testSimpleAndroidAndBasePlugin() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleAndroidAndBasePlugin"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleAndroidAndBasePluginExpected"))
  }

  @Test
  fun testIsNotAlwaysNoOpOnSimpleApplication() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleApplication"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    assertFalse(processor.isAlwaysNoOpForProject)
  }

  @Test
  fun testIsAlwaysNoOpOnSimpleApplicationExpected() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleApplicationExpected"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    assertTrue(processor.isAlwaysNoOpForProject)
  }

  @Test
  fun testIsNotAlwaysNoOpOnApplicationWith2DVariant() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/ApplicationWith2DVariant"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    assertFalse(processor.isAlwaysNoOpForProject)
  }

  @Test
  fun testIsAlwaysNoOpOnUnknownPlugin() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/UnknownPlugin"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    assertTrue(processor.isAlwaysNoOpForProject)
  }

  @Test
  fun testIsNotAlwaysNoOpOnBuildscriptDependenciesLeftAlone() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/BuildscriptDependenciesLeftAlone"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("7.0.0"))
    // the test file has some dependencies needing update not in the buildscript block
    assertFalse(processor.isAlwaysNoOpForProject)
  }

  @Test
  fun testTooltipsNotNull() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/ApplicationWith2DVariant"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("5.0.0"))
    val usages = processor.findUsages()
    assertTrue(usages.isNotEmpty())
    usages.forEach { assertNotNull(it.tooltipText) }
  }
}