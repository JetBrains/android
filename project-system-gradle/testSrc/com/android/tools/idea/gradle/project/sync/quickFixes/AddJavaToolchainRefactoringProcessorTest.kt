/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.quickFixes

import com.android.tools.idea.gradle.GradleFileModelTestCase
import com.google.common.truth.Truth
import com.intellij.openapi.vfs.readText
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestBase
import org.junit.Before
import org.junit.Test

@RunsInEdt
class AddJavaToolchainRefactoringProcessorTest : GradleFileModelTestCase() {

  @Before
  fun setUpTestDataPath() {
    testDataPath = AndroidTestBase.getModulePath("project-system-gradle") + "/testData/refactorings"
  }

  @Test
  fun addingNewDefinition() {
    writeToBuildFile(TestFileName("JavaToolchain/appNoJavaBlock"))

    val processor = AddJavaToolchainDefinition(project, 17, listOf(projectRule.module))
    processor.run()

    verifyFileContents(buildFile, TestFileName("JavaToolchain/appToolchain17Defined"))
  }

  @Test
  fun updateExistingDefinition() {
    writeToBuildFile(TestFileName("JavaToolchain/appToolchain17Defined"))

    val processor = AddJavaToolchainDefinition(project, 21, listOf(projectRule.module))
    processor.run()

    verifyFileContents(buildFile, TestFileName("JavaToolchain/appToolchain21Defined"))
  }

  @Test
  fun updateExistingDefinitionDefinedInKotlinBlock() {
    writeToBuildFile(TestFileName("JavaToolchain/appToolchain17DefinedInKotlinBlock"))

    val processor = AddJavaToolchainDefinition(project, 21, listOf(projectRule.module))
    processor.run()

    verifyFileContents(buildFile, TestFileName("JavaToolchain/appToolchain21DefinedInKotlinBlock"))
  }

  @Test
  fun updateExistingDefinitionDefinedInBothJavaAndKotlinBlocks() {
    writeToBuildFile(TestFileName("JavaToolchain/appToolchain17DefinedInBothJavaAndKotlinBlocks"))

    val processor = AddJavaToolchainDefinition(project, 21, listOf(projectRule.module))
    processor.run()

    verifyFileContents(buildFile, TestFileName("JavaToolchain/appToolchain21DefinedInBothJavaAndKotlinBlocks"))
  }

  @Test
  fun addingOnlyToRequestedModules() {
    writeToBuildFile(TestFileName("JavaToolchain/appNoJavaBlock"))

    val processor = AddJavaToolchainDefinition(project, 17, emptyList())
    processor.run()

    verifyFileContents(buildFile, TestFileName("JavaToolchain/appNoJavaBlock"))
  }

  @Test
  fun addDefaultResolverPluginToSettings() {
    writeToSettingsFile(TestFileName("JavaToolchain/settingsEmpty"))
    writeToBuildFile(TestFileName("JavaToolchain/appNoJavaBlock"))

    val processor = AddJavaToolchainDefinition(project, 17, listOf(projectRule.module))
    processor.run()

    verifyFileContents(settingsFile, TestFileName("JavaToolchain/settingsWithFooJayResolverConventionApplied"))
  }

  @Test
  fun notTouchingSettingsIfPluginAlreadyAdded() {
    writeToSettingsFile(TestFileName("JavaToolchain/settingsWithFooJayResolverConventionApplied"))
    writeToBuildFile(TestFileName("JavaToolchain/appNoJavaBlock"))

    val processor = AddJavaToolchainDefinition(project, 17, listOf(projectRule.module))
    processor.run()

    verifyFileContents(settingsFile, TestFileName("JavaToolchain/settingsWithFooJayResolverConventionApplied"))
  }

  @Test
  fun addDefaultResolverPluginToSettingsWhenProjectWithCatalog() {
    writeToSettingsFile(TestFileName("JavaToolchain/settingsEmpty"))
    writeToBuildFile(TestFileName("JavaToolchain/appNoJavaBlock"))
    createCatalogFile("gradle/libs.versions.toml")

    val processor = AddJavaToolchainDefinition(project, 17, listOf(projectRule.module))
    processor.run()

    verifyFileContents(settingsFile, TestFileName("JavaToolchain/settingsWithFooJayResolverConventionAppliedCatalog"))
    Truth.assertThat(catalogFile.readText()).isEmpty()
  }

}