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
package com.android.build.attribution.ui.controllers

import com.android.build.attribution.ui.view.details.JetifierWarningDetailsView
import com.android.tools.idea.gradle.GradleFileModelTestCase
import com.google.common.truth.Truth
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.junit.Before
import org.junit.Test

/**
 * This test covers search logic works as expected on different options of dependency declarations.
 */
@RunsInEdt
class FindSelectedLibVersionDeclarationActionTest : GradleFileModelTestCase() {

  @Before
  fun setUpTestDataPath() {
    testDataPath = AndroidTestBase.getModulePath("build-attribution") + "/testData/buildFiles"
  }

  private val selectedDependency = JetifierWarningDetailsView.DirectDependencyDescriptor(
    fullName = "org.jetbrains.kotlin:kotlin-stdlib:1.5.31",
    projects = listOf(":"),
    pathToSupportLibrary = listOf() // Does not get involved here.
  )

  @Test
  fun testVersionInLiteral() {
    writeToBuildFile(TestFileName("libraries/versionInLiteral"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(1)
  }

  @Test
  fun testVersionInMapNotation() {
    writeToBuildFile(TestFileName("libraries/versionInMapNotation"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(1)
  }

  @Test
  fun testVersionInVariable() {
    writeToBuildFile(TestFileName("libraries/versionInVariable"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testVersionInUnknownVariable() {
    writeToBuildFile(TestFileName("libraries/versionInUnknownVariable"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    // In case of problems with resolving version return declaration itself.
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(2)
  }

  @Test
  fun testSeveralDeclarationsVersionInLiteral() {
    writeToBuildFile(TestFileName("libraries/versionInLiteralSeveralDeclarations"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    // Return several all declarations when they are defined separately.
    Truth.assertThat(arrayOfUsageInfos).hasLength(2)
  }

  @Test
  fun testSeveralDeclarationsVersionInLiteralDifferentVersions() {
    writeToBuildFile(TestFileName("libraries/versionInLiteralSeveralDeclarationsDifferentVersions"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    // Look for all declarations in one file (module) not depending on version.
    Truth.assertThat(arrayOfUsageInfos).hasLength(2)
  }

  @Test
  fun testSeveralDeclarationsVersionInOneVariable() {
    writeToBuildFile(TestFileName("libraries/versionInVariableSeveralDeclarations"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    // Return one single version variable declaration references from both dependency declarations.
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testDependencyInVariable() {
    writeToBuildFile(TestFileName("libraries/dependencyInVariable"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testDependencyInMap() {
    writeToBuildFile(TestFileName("libraries/dependencyInMap"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(1)
  }

  @Test
  fun testDependencyAsReferenceMap() {
    writeToBuildFile(TestFileName("libraries/dependencyAsReferenceMap"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testDependencyInVariableVersionInVariable() {
    writeToBuildFile(TestFileName("libraries/dependencyInVariableVersionInVariable"))
    val arrayOfUsageInfos = findVersionDeclarations(project, selectedDependency)
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }
}
