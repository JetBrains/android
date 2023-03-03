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
package com.android.tools.idea.lint

import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import java.io.File
import org.jetbrains.android.AndroidTestBase

object TestDataPaths {
  @JvmField
  val TEST_DATA_ROOT =
    TestUtils.resolveWorkspacePath("tools/adt/idea/android-lint/testData").toString()
  const val BASIC_CMAKE_APP = "projects/basicCmakeApp"
  const val COMPOSITE_BUILD = "projects/compositeBuild"
  const val KOTLIN_KAPT = "projects/kotlinKapt"
  const val LINKED = "projects/linked"
  const val LINT_CUSTOM_CHECKS = "projects/lintCustomChecks"
  const val MULTI_FLAVOR = "projects/multiFlavor"
  const val NON_STANDARD_SOURCE_SETS = "projects/nonStandardSourceSets"
  const val PSD_SAMPLE_GROOVY = "projects/psdSample/Groovy"
  const val SIMPLE_APPLICATION = "projects/simpleApplication"
  const val PSD_SAMPLE_REPO = "projects/psdSampleRepo"
  const val TEST_FIXTURES = "projects/testFixtures"
}

enum class LintTestProject(
  override val template: String,
  override val pathToOpen: String = "",
  override val testName: String? = null,
  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = { true },
  override val autoMigratePackageAttribute: Boolean = true,
  override val setup: () -> () -> Unit = { {} },
  override val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit = {},
  override val expectedSyncIssues: Set<Int> = emptySet(),
  override val verifyOpened: ((Project) -> Unit)? = null
) : TemplateBasedTestProject {
  BASIC_CMAKE_APP(TestDataPaths.BASIC_CMAKE_APP),
  COMPOSITE_BUILD(TestDataPaths.COMPOSITE_BUILD),
  KOTLIN_KAPT(TestDataPaths.KOTLIN_KAPT),
  LINKED(TestDataPaths.LINKED, pathToOpen = "/firstapp"),
  LINT_CUSTOM_CHECKS(TestDataPaths.LINT_CUSTOM_CHECKS),
  MULTI_FLAVOR(TestDataPaths.MULTI_FLAVOR),
  NON_STANDARD_SOURCE_SETS(TestDataPaths.NON_STANDARD_SOURCE_SETS, pathToOpen = "/application"),
  PSD_SAMPLE_GROOVY(TestDataPaths.PSD_SAMPLE_GROOVY),
  SIMPLE_APPLICATION(TestDataPaths.SIMPLE_APPLICATION),
  TEST_FIXTURES(TestDataPaths.TEST_FIXTURES),
  ;

  override fun getTestDataDirectoryWorkspaceRelativePath(): String =
    "tools/adt/idea/android/testData/snapshots"

  override fun getAdditionalRepos(): Collection<File> =
    listOf(
      File(
        AndroidTestBase.getTestDataPath(),
        PathUtil.toSystemDependentName(TestDataPaths.PSD_SAMPLE_REPO)
      )
    )
}
