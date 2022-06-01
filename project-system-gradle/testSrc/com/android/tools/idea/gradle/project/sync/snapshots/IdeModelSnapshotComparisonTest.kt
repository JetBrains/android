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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension
import com.android.tools.idea.gradle.project.sync.internal.dumpAndroidIdeModel
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_42
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_70
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_71
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_72
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.saveAndDump
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.annotations.Contract
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Snapshot tests for 'Ide Models'.
 *
 * These tests convert Ide models to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and compare them to pre-recorded golden
 * results.
 *
 * The pre-recorded sync results can be found in [snapshotDirectoryWorkspaceRelativePath] *.txt files.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest] and if running from the command-line use
 * target as "//tools/adt/idea/android:intellij.android.core.tests_tests__all --test_filter=IdeModelSnapshotComparisonTest".
 */

@RunsInEdt
@RunWith(Parameterized::class)
open class IdeModelSnapshotComparisonTest : GradleIntegrationTest, SnapshotComparisonTest {

  data class TestProject(
    val template: String,
    val pathToOpen: String = "",
    val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = { true },
    override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AGP_CURRENT,
  ) : AgpIntegrationTestDefinition {
    override val name: String
      get() = "${template.removePrefix("projects/")}${pathToOpen}${agpVersion.agpSuffix()}${agpVersion.gradleSuffix()}"

    override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): AgpIntegrationTestDefinition {
      return copy(agpVersion = agpVersion)
    }

    override fun toString(): String = name

    override fun isCompatible(): Boolean = isCompatibleWith(agpVersion)
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testProjectName: TestProject? = null

  companion object {
    private val projectsList = listOf(
      TestProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION),
      TestProject(TestProjectToSnapshotPaths.WITH_GRADLE_METADATA),
      TestProject(TestProjectToSnapshotPaths.BASIC_CMAKE_APP),
      TestProject(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY),
      // Composite Build project cannot Sync using legacy Gradle due to duplicate root issue: https://github.com/gradle/gradle/issues/18874
      TestProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD, isCompatibleWith = { it >= AGP_70 }),
      TestProject(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS, "/application"),
      TestProject(TestProjectToSnapshotPaths.LINKED, "/firstapp"),
      TestProject(TestProjectToSnapshotPaths.KOTLIN_KAPT),
      TestProject(TestProjectToSnapshotPaths.LINT_CUSTOM_CHECKS, isCompatibleWith = { it !in setOf(AGP_41, AGP_42, AGP_70) }),
      // Test Fixtures support is available through AGP 7.2 and above.
      TestProject(TestProjectToSnapshotPaths.TEST_FIXTURES, isCompatibleWith = { it !in setOf(AGP_41, AGP_42, AGP_70, AGP_71) }),
      TestProject(TestProjectToSnapshotPaths.TEST_ONLY_MODULE),
      TestProject(TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM, isCompatibleWith = { it !in setOf(AGP_41, AGP_42) }),
      TestProject(TestProjectToSnapshotPaths.MULTI_FLAVOR),
      TestProject(TestProjectToSnapshotPaths.NAMESPACES),
      TestProject(TestProjectToSnapshotPaths.INCLUDE_FROM_LIB),
      TestProject(TestProjectToSnapshotPaths.LOCAL_AARS_AS_MODULES),
      TestProject(TestProjectToSnapshotPaths.BASIC)
      )

    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testParameters(): Collection<*> {
      return testProjects().filter { it.isCompatible() }.map { listOf(it).toTypedArray() }
    }

    const val NUMBER_OF_EXPECTATIONS = 2  // Apk and ApplicationIdProvider's.
    fun testProjects(): List<TestProject> = projectsList
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))

  override fun getAgpVersionSoftwareEnvironmentDescriptor(): AgpVersionSoftwareEnvironmentDescriptor {
    return testProjectName?.agpVersion ?: AGP_CURRENT
  }

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/ideModels"

  @Test
  fun testIdeModels() {
    StudioFlags.GRADLE_SYNC_USE_V2_MODEL.override(false)
    try {
      val projectName = testProjectName ?: error("unit test parameter not initialized")
      val root = prepareGradleProject(
        projectName.template,
        "project",
        ndkVersion = SdkConstants.NDK_DEFAULT_VERSION
      )
      CapturePlatformModelsProjectResolverExtension.registerTestHelperProjectResolver(projectRule.fixture.testRootDisposable)
      openPreparedProject("project${testProjectName?.pathToOpen}") { project ->
        val dump = project.saveAndDump(mapOf("ROOT" to root)) { project, projectDumper ->
          projectDumper.dumpAndroidIdeModel(
            project,
            kotlinModels = { CapturePlatformModelsProjectResolverExtension.getKotlinModel(it) },
            kaptModels = { CapturePlatformModelsProjectResolverExtension.getKaptModel(it) },
            externalProjects = { CapturePlatformModelsProjectResolverExtension.getExternalProjectModel(it) }
          )
        }
        assertIsEqualToSnapshot(dump)
      }
    } finally {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.clearOverride()
    }
  }
}

private fun AgpVersionSoftwareEnvironmentDescriptor.agpSuffix(): String = when(this) {
  AGP_CURRENT -> "_NewAgp"
  AGP_35 -> "_Agp_3.5"
  AGP_40 -> "_Agp_4.0"
  AGP_41 -> "_Agp_4.1"
  AGP_42 -> "_Agp_4.2"
  AGP_70 -> "_Agp_7.0"
  AGP_71 -> "_Agp_7.1"
  AGP_72 -> "_Agp_7.2"
}

private fun AgpVersionSoftwareEnvironmentDescriptor.gradleSuffix(): String = gradleVersion?.let {"_Gradle_$it"}.orEmpty()

