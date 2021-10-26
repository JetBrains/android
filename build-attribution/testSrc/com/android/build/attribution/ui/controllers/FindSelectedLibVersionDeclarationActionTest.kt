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

import com.android.tools.idea.gradle.GradleFileModelTestCase
import com.google.common.truth.Truth
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.kotlin.idea.core.util.getLineNumber
import org.junit.Before
import org.junit.Test

@RunsInEdt
class FindSelectedLibVersionDeclarationActionTest : GradleFileModelTestCase(){

  @Before
  fun setUpTestDataPath() {
    testDataPath = AndroidTestBase.getModulePath("build-attribution") + "/testData/buildFiles"
  }

  @Test
  fun testVersionInLiteral() {
    writeToBuildFile(TestFileName("libraries/versionInLiteral"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(1)
  }

  @Test
  fun testVersionInMapNotation() {
    writeToBuildFile(TestFileName("libraries/versionInMapNotation"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(1)
  }

  @Test
  fun testVersionInVariable() {
    writeToBuildFile(TestFileName("libraries/versionInVariable"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testVersionInUnknownVariable() {
    writeToBuildFile(TestFileName("libraries/versionInUnknownVariable"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    // In case of problems with resolving version return declaration itself.
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(2)
  }

  @Test
  fun testSeveralDeclarationsVersionInLiteral() {
    writeToBuildFile(TestFileName("libraries/versionInLiteralSeveralDeclarations"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    // Return several all declarations when they are defined separately.
    Truth.assertThat(arrayOfUsageInfos).hasLength(2)
  }

  @Test
  fun testSeveralDeclarationsVersionInOneVariable() {
    writeToBuildFile(TestFileName("libraries/versionInVariableSeveralDeclarations"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    // Return one single version variable declaration references from both dependency declarations.
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testDependencyInVariable() {
    writeToBuildFile(TestFileName("libraries/dependencyInVariable"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testDependencyInMap() {
    writeToBuildFile(TestFileName("libraries/dependencyInMap"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(1)
  }

  @Test
  fun testDependencyAsReferenceMap() {
    writeToBuildFile(TestFileName("libraries/dependencyAsReferenceMap"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testDependencyInVariableVersionInVariable() {
    writeToBuildFile(TestFileName("libraries/dependencyInVariableVersionInVariable"))
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, project)
    val arrayOfUsageInfos = action.findVersionDeclarations("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }
}