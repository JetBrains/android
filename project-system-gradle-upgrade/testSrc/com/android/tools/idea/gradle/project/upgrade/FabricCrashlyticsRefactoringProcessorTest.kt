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
import com.intellij.testFramework.RunsInEdt
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertTrue
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

@RunsInEdt
class FabricCrashlyticsRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("3.1.0" to "3.2.0") to AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE,
      ("3.1.0" to "4.2.0") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
      ("4.1.0" to "4.2.0") to AgpUpgradeComponentNecessity.IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      Assert.assertEquals(u, processor.necessity())
    }
  }

  @Test
  fun testReadMoreUrl() {
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/fabric-crashlytics", processor.getReadMoreUrl())
  }

  @Test
  fun testClasspathDependencies() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricClasspathDependencies"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricClasspathDependenciesExpected"))
  }

  @Test
  fun testIsAlwaysNoOpOnFabricClasspathDependenciesExpected() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricClasspathDependenciesExpected"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    Assert.assertTrue(processor.isAlwaysNoOpForProject)
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricClasspathDependenciesExpected"))
  }

  @Test
  fun testFabricSdk() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdk"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricSdkExpected"))
  }

  @Test
  fun testIsAlwaysNoOpOnFabricSdkExpected() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdkExpected"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    Assert.assertTrue(processor.isAlwaysNoOpForProject)
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricSdkExpected"))
  }

  @Test
  fun testFabricSdkWithNdk() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdkWithNdk"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricSdkWithNdkExpected"))
  }

  @Test
  fun testFabricSdkWithNdkAndFirebaseDependencies() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdkWithNdkAndFirebaseDependencies"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricSdkWithNdkAndFirebaseDependenciesExpected"))
  }

  @Test
  fun testIsAlwaysNoOpOnFabricSdkWithNdkExpected() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdkWithNdkExpected"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    Assert.assertTrue(processor.isAlwaysNoOpForProject)
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricSdkWithNdkExpected"))
  }

  @Test
  fun testIsAlwaysNoOpOnNonFabric() {
    writeToBuildFile(TestFileName("FabricCrashlytics/NonFabric"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    Assert.assertTrue(processor.isAlwaysNoOpForProject)
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/NonFabric"))
  }

  @Test
  fun testFabricClasspathDependenciesTooltipsNotNull() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricClasspathDependencies"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    val usages = processor.findUsages()
    assertTrue(usages.isNotEmpty())
    usages.forEach { assertNotNull(it.tooltipText) }
  }

  @Test
  fun testFabricSdkWithNdkTooltipsNotNull() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdkWithNdk"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    val usages = processor.findUsages()
    assertTrue(usages.isNotEmpty())
    usages.forEach { assertNotNull(it.tooltipText) }
  }
}