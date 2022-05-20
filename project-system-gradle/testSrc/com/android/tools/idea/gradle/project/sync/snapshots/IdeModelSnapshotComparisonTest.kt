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
    val incompatibleWithAgps: Set<AgpVersion> = emptySet(),
    val incompatibleWithGradle: Set<GradleVersion> = emptySet()
  ) {
    override fun toString(): String = "${template.removePrefix("projects/")}$pathToOpen"
  }

  enum class AgpVersion(
    val suffix: String,
    val legacyAgpVersion: String? = null
  ) {
    CURRENT("NewAgp"),
    LEGACY_4_1("Agp_4.1", "4.1.0"),
    LEGACY_4_2("Agp_4.2", "4.2.0"),
    LEGACY_7_0("Agp_7.0", "7.0.0"),
    LEGACY_7_1("Agp_7.1", "7.1.0"),
    LEGACY_7_2("Agp_7.2", "7.2.0"),
    ;

    override fun toString(): String = suffix
  }

  enum class GradleVersion(
    val suffix: String,
    val legacyGradleVersion: String? = null
  ) {
    CURRENT("LATEST"),
    LEGACY_6_5("Gradle_6.5", "6.5"),
    LEGACY_6_7_1("Gradle_6.7.1", "6.7.1"),
    LEGACY_7_0_2("Gradle_7.0.2", "7.0.2"),
    LEGACY_7_2("Gradle_7.2", "7.2"),
    LEGACY_7_3_3("Gradle_7.3.3", "7.3.3"),
    ;

    override fun toString(): String = suffix
  }

  @JvmField
  @Parameterized.Parameter(0)
  var agpVersion: AgpVersion? = null

  @JvmField
  @Parameterized.Parameter(2)
  var gradleVersion: GradleVersion? = null

  @JvmField
  @Parameterized.Parameter(1)
  var testProjectName: TestProject? = null

  companion object {
    private val projectsList = listOf(
      TestProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION),
      TestProject(TestProjectToSnapshotPaths.WITH_GRADLE_METADATA),
      TestProject(TestProjectToSnapshotPaths.BASIC_CMAKE_APP),
      TestProject(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY),
      // Composite Build project cannot Sync using legacy Gradle due to duplicate root issue: https://github.com/gradle/gradle/issues/18874
      TestProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD, incompatibleWithGradle = setOf(GradleVersion.LEGACY_6_7_1, GradleVersion.LEGACY_6_5)),
      TestProject(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS, "/application"),
      TestProject(TestProjectToSnapshotPaths.LINKED, "/firstapp"),
      TestProject(TestProjectToSnapshotPaths.KOTLIN_KAPT),
      TestProject(TestProjectToSnapshotPaths.LINT_CUSTOM_CHECKS, incompatibleWithAgps = setOf(AgpVersion.LEGACY_4_1, AgpVersion.LEGACY_4_2, AgpVersion.LEGACY_7_0)),
      // Test Fixtures support is available through AGP 7.2 and above.
      TestProject(TestProjectToSnapshotPaths.TEST_FIXTURES, incompatibleWithAgps = setOf(AgpVersion.LEGACY_4_1, AgpVersion.LEGACY_4_2, AgpVersion.LEGACY_7_0, AgpVersion.LEGACY_7_1)),
      TestProject(TestProjectToSnapshotPaths.TEST_ONLY_MODULE),
      TestProject(TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM, incompatibleWithAgps = setOf(AgpVersion.LEGACY_4_1, AgpVersion.LEGACY_4_2)),
      TestProject(TestProjectToSnapshotPaths.MULTI_FLAVOR),
      TestProject(TestProjectToSnapshotPaths.NAMESPACES),
      TestProject(TestProjectToSnapshotPaths.INCLUDE_FROM_LIB),
      TestProject(TestProjectToSnapshotPaths.LOCAL_AARS_AS_MODULES),
      TestProject(TestProjectToSnapshotPaths.BASIC)
      )

    fun testProjectsFor(agpAndGradleVersions: Map<AgpVersion, GradleVersion>) =
      agpAndGradleVersions
        .flatMap { agpAndGradleVersion ->
          projectsList
            .filter { agpAndGradleVersion.key !in it.incompatibleWithAgps }
            .filter { agpAndGradleVersion.value !in it.incompatibleWithGradle }
            .map { listOf(agpAndGradleVersion.key, it, agpAndGradleVersion.value).toTypedArray() }
        }

    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{1}\${0}")
    fun testProjects(): Collection<*> = testProjectsFor(
      AgpVersion.values().filter { it == AgpVersion.CURRENT }.zip(
      GradleVersion.values().filter { it == GradleVersion.CURRENT }).toMap()
    )
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

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/ideModels"

  @Test
  fun testIdeModels() {
    StudioFlags.GRADLE_SYNC_USE_V2_MODEL.override(false)
    try {
      val projectName = testProjectName ?: error("unit test parameter not initialized")
      val agpVersion = agpVersion ?: error("unit test parameter not initialized")
      val gradleVersion = gradleVersion ?: error("unit test parameter not initialized")
      val root = prepareGradleProject(
        projectName.template,
        "project",
        gradleVersion.legacyGradleVersion,
        agpVersion.legacyAgpVersion,
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