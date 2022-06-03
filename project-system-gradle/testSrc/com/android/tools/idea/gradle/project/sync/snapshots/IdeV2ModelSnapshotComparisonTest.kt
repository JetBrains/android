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

import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension
import com.android.tools.idea.gradle.project.sync.internal.dumpAndroidIdeModel
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTest.TestDef
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.getAndMaybeUpdateSnapshot
import com.android.tools.idea.testing.nameProperties
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Snapshot test definitions for 'Ide Models' (To run tests see [SyncedProjectTest])
 *
 * These tests convert Ide models to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and compare them to pre-recorded golden
 * results.
 *
 * The pre-recorded sync results can be found in *.txt files under testData/snapshots/{ideModels,v2IdeModels}.
 *
 * For instructions on how to update the snapshot files see `SnapshotComparisonTest` and if running from the command-line use
 * target as "//tools/adt/idea/android:intellij.android.core.tests_tests__all --test_filter=IdeV2ModelSnapshotComparisonTest".
 */
data class IdeModelV2TestDef(
  override val testProject: TestProject,
  val skipV1toV2Comparison: Boolean = false,
  val v1toV2PropertiesToSkip: Set<String> = emptySet(),
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
) : TestDef {

  class SnapshotContext(
    private val name: String,
    workspace: String = "tools/adt/idea/android/testData/snapshots/v2IdeModels"
  ) :
    SnapshotComparisonTest {
    override val snapshotDirectoryWorkspaceRelativePath: String = workspace
    override fun getName(): String = name
  }

  override val name: String = testProject.projectName

  override fun toString(): String = testProject.projectName

  override fun runTest(root: File, project: Project) {
    val adjustedTestName = "ideModels_" + testProject.projectName + "_"
    val v2snapshots = SnapshotContext(adjustedTestName)
    val dump = project.saveAndDump(mapOf("ROOT" to root)) { project, projectDumper ->
      projectDumper.dumpAndroidIdeModel(
        project,
        kotlinModels = { CapturePlatformModelsProjectResolverExtension.getKotlinModel(it) },
        kaptModels = { CapturePlatformModelsProjectResolverExtension.getKaptModel(it) },
        externalProjects = { CapturePlatformModelsProjectResolverExtension.getExternalProjectModel(it) }
      )
    }
    v2snapshots.assertIsEqualToSnapshot(dump)

    testV1vsV2()
  }

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): AgpIntegrationTestDefinition {
    return copy(agpVersion = agpVersion)
  }

  private fun testV1vsV2() {
    if (skipV1toV2Comparison) return

    fun String.filterOutProperties(): String =
      this
        .splitToSequence('\n')
        .nameProperties()
        .filter { (property, line) ->
          !PROPERTIES_TO_SKIP.any { property.endsWith(it) } &&
            !ENTITIES_TO_SKIP.any { property.contains(it) } &&
            !v1toV2PropertiesToSkip.any { property.endsWith(it) }
        }
        .filter { (property, line) -> !VALUES_TO_SUPPRESS.any { property.endsWith(it.key) and it.value.any { value -> line.contains(value) } } }
        .map { it.first + " <> " + it.second }
        .joinToString(separator = "\n")

    val adjustedTestName = "ideModels_" + testProject.projectName
    val v1snapshots = SnapshotContext(adjustedTestName, "tools/adt/idea/android/testData/snapshots/ideModels")
    val (_, expectedTextV1) = v1snapshots.getAndMaybeUpdateSnapshot("_NewAgp_", "", doNotUpdate = true)

    val v2snapshots = SnapshotContext(adjustedTestName)
    val (_, expectedTextV2) = v2snapshots.getAndMaybeUpdateSnapshot("_", "", doNotUpdate = true)


    val expectedTextV2Filtered = expectedTextV2.filterOutProperties()
    val expectedTextV1Filtered = expectedTextV1.filterOutProperties()

    Truth.assertThat(expectedTextV2Filtered).isEqualTo(expectedTextV1Filtered)
  }

  companion object {
    fun tests(): List<IdeModelV2TestDef> = listOf(
      IdeModelV2TestDef(TestProject.SIMPLE_APPLICATION),
      IdeModelV2TestDef(TestProject.SIMPLE_APPLICATION_WITH_ADDITIONAL_GRADLE_SOURCE_SETS, skipV1toV2Comparison = true),
      IdeModelV2TestDef(TestProject.WITH_GRADLE_METADATA),
      IdeModelV2TestDef(TestProject.BASIC_CMAKE_APP),
      IdeModelV2TestDef(TestProject.PSD_SAMPLE_GROOVY),
      IdeModelV2TestDef(TestProject.COMPOSITE_BUILD),
      IdeModelV2TestDef(TestProject.NON_STANDARD_SOURCE_SETS),
      IdeModelV2TestDef(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES, skipV1toV2Comparison = true),
      IdeModelV2TestDef(TestProject.LINKED),
      IdeModelV2TestDef(TestProject.KOTLIN_KAPT),
      IdeModelV2TestDef(TestProject.LINT_CUSTOM_CHECKS),
      IdeModelV2TestDef(TestProject.TEST_FIXTURES, skipV1toV2Comparison = true),
      // Ignore comparing the variant name for module dependencies because this is not always provided by V1 models.
      IdeModelV2TestDef(
        TestProject.TEST_ONLY_MODULE,
        v1toV2PropertiesToSkip =
        setOf(
          "moduleDependencies",
          "moduleDependencies/target",
          "moduleDependencies/target/buildId",
          "moduleDependencies/target/projectPath",
          "moduleDependencies/target/sourceSet"
        )
      ),
      IdeModelV2TestDef(TestProject.KOTLIN_MULTIPLATFORM),
      IdeModelV2TestDef(TestProject.MULTI_FLAVOR),
      // Skip V1 and V2 comparison for namespace project. The support for namespace in V2 is stricter since ag/16005984. more info b/111168382.
      IdeModelV2TestDef(TestProject.NAMESPACES, skipV1toV2Comparison = true),
      IdeModelV2TestDef(TestProject.INCLUDE_FROM_LIB),
      IdeModelV2TestDef(TestProject.LOCAL_AARS_AS_MODULES, v1toV2PropertiesToSkip = setOf("provided")), // Skip __wrapped_aars__.
      IdeModelV2TestDef(TestProject.BASIC)
    )
  }
}

private fun Sequence<String>.nameProperties() = nameProperties(this)

/**
 * we skip:
 * [com.android.tools.idea.gradle.model.IdeAndroidLibrary.lintJar] because in V2 we do check that the jar exists before populating the property.
 * [com.android.tools.idea.gradle.model.IdeVariant.deprecatedPreMergedApplicationId] as not present in V2
 * [com.android.tools.idea.gradle.model.IdeVariant.deprecatedPreMergedTestApplicationId] as not present in V2
 * [com.android.builder.model.v2.ModelSyncFile] as these are not present in V1.
 * `runetimeClasspath` as it is not available in V1.
 */
private val PROPERTIES_TO_SKIP = setOf(
  "/Dependencies/compileClasspath/androidLibraries/target/lintJar",
  "MODULE/IdeVariants/IdeVariant/DeprecatedPreMergedApplicationId",
  "MODULE/IdeVariants/IdeVariant/DeprecatedPreMergedTestApplicationId"
)

private val ENTITIES_TO_SKIP = setOf(
  "/Dependencies/compileClasspath/moduleDependencies/target",
  "/Dependencies/runtimeClasspath",
  "/MainArtifact/ModelSyncFile",
)

/**
 * some properties values have different patterns in V1 and V2, and we do suppress them.
 * AndroidLibrary: in V2 we added better support for wrapped_aars and composite builds in the libraries names,
 *                  so this will end up being different from V1 where we do not have support for composite builds,
 *                  and the names are prefixed with "artifacts" instead.
 * AndroidLibrary.ArtifactAddress: the same rules from above apply to ArtifactAddress as well, plus the distinction of local aars paths.
 */
private val VALUES_TO_SUPPRESS = mapOf(
  "/Dependencies/compileClasspath/androidLibraries" to listOf("__wrapped_aars__", "artifacts"),
  "/Dependencies/compileClasspath/androidLibraries/target" to listOf("__wrapped_aars__", "artifacts"),
  "/Dependencies/compileClasspath/androidLibraries/target/artifactAddress" to listOf("__local_aars__", "__wrapped_aars__", "artifacts")
)
