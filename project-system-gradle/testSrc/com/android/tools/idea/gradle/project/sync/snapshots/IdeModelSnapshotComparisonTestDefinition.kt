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
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_73
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_32
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_42
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_70
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_71
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_72
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_72_V1
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_80
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_80_V1
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT_V1
import com.android.tools.idea.testing.ModelVersion
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
 * environment (and ideally should not depend on the versions of irrelev ant libraries) and compare them to pre-recorded golden
 * results.
 *
 * The pre-recorded sync results can be found in *.txt files under testData/snapshots/{ideModels,v2IdeModels}.
 *
 * For instructions on how to update the snapshot files see `SnapshotComparisonTest` and if running from the command-line use
 * target as "//tools/adt/idea/android:intellij.android.core.tests_tests__all --test_filter=IdeV2ModelSnapshotComparisonTest".
 */
data class IdeModelSnapshotComparisonTestDefinition(
  override val testProject: TestProject,
  val skipV1toV2Comparison: Boolean = false,
  val v1toV2PropertiesToSkip: Set<String> = emptySet(),
  val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = {  it >= AGP_41 },
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AGP_CURRENT,
) : TestDef {

  companion object {
    fun tests(): List<IdeModelSnapshotComparisonTestDefinition> = listOf(
      IdeModelSnapshotComparisonTestDefinition(TestProject.SIMPLE_APPLICATION),
      IdeModelSnapshotComparisonTestDefinition(TestProject.SIMPLE_APPLICATION_VIA_SYMLINK),
      IdeModelSnapshotComparisonTestDefinition(TestProject.SIMPLE_APPLICATION_APP_VIA_SYMLINK),
      IdeModelSnapshotComparisonTestDefinition(
        TestProject.SIMPLE_APPLICATION_WITH_ADDITIONAL_GRADLE_SOURCE_SETS,
        skipV1toV2Comparison = true,
        isCompatibleWith = { it.modelVersion == ModelVersion.V2 }),
      IdeModelSnapshotComparisonTestDefinition(
        TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT,
        skipV1toV2Comparison = true
      ),
      IdeModelSnapshotComparisonTestDefinition(
        TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS,
        skipV1toV2Comparison = true
      ),
      IdeModelSnapshotComparisonTestDefinition(
        TestProject.SIMPLE_APPLICATION_WITH_UNNAMED_DIMENSION,
        skipV1toV2Comparison = true
      ),
      IdeModelSnapshotComparisonTestDefinition(
        TestProject.TRANSITIVE_DEPENDENCIES_NO_TARGET_SDK_IN_LIBS,
          isCompatibleWith = { it >= AGP_35 }
      ),
      IdeModelSnapshotComparisonTestDefinition(TestProject.WITH_GRADLE_METADATA),
      IdeModelSnapshotComparisonTestDefinition(TestProject.BASIC_CMAKE_APP),
      IdeModelSnapshotComparisonTestDefinition(TestProject.PSD_SAMPLE_GROOVY),
      IdeModelSnapshotComparisonTestDefinition(TestProject.COMPOSITE_BUILD),
      IdeModelSnapshotComparisonTestDefinition(TestProject.NON_STANDARD_SOURCE_SETS),
      IdeModelSnapshotComparisonTestDefinition(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES, skipV1toV2Comparison = true),
      IdeModelSnapshotComparisonTestDefinition(
        TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES_MANUAL_TEST_FIXTURES_WORKAROUND,
        skipV1toV2Comparison = true
      ),
      IdeModelSnapshotComparisonTestDefinition(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES_HIERARCHICAL, skipV1toV2Comparison = true),
      IdeModelSnapshotComparisonTestDefinition(TestProject.LINKED),
      IdeModelSnapshotComparisonTestDefinition(TestProject.KOTLIN_KAPT),
      IdeModelSnapshotComparisonTestDefinition(TestProject.LINT_CUSTOM_CHECKS),
      IdeModelSnapshotComparisonTestDefinition(TestProject.TEST_FIXTURES, skipV1toV2Comparison = true),
      // Ignore comparing the variant name for module dependencies because this is not always provided by V1 models.
      IdeModelSnapshotComparisonTestDefinition(
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
      IdeModelSnapshotComparisonTestDefinition(TestProject.KOTLIN_MULTIPLATFORM),
      IdeModelSnapshotComparisonTestDefinition(TestProject.KOTLIN_MULTIPLATFORM_HIERARCHICAL, skipV1toV2Comparison = true),
      IdeModelSnapshotComparisonTestDefinition(TestProject.KOTLIN_MULTIPLATFORM_HIERARCHICAL_WITHJS, skipV1toV2Comparison = true),
      IdeModelSnapshotComparisonTestDefinition(TestProject.KOTLIN_MULTIPLATFORM_JVM, skipV1toV2Comparison = true),
      IdeModelSnapshotComparisonTestDefinition(TestProject.KOTLIN_MULTIPLATFORM_JVM_HIERARCHICAL, skipV1toV2Comparison = true),
      IdeModelSnapshotComparisonTestDefinition(TestProject.KOTLIN_MULTIPLATFORM_JVM_HIERARCHICAL_KMPAPP, skipV1toV2Comparison = true),
      IdeModelSnapshotComparisonTestDefinition(
        TestProject.KOTLIN_MULTIPLATFORM_JVM_HIERARCHICAL_KMPAPP_WITHINTERMEDIATE,
        skipV1toV2Comparison = true
      ),
      IdeModelSnapshotComparisonTestDefinition(TestProject.MULTI_FLAVOR),
      IdeModelSnapshotComparisonTestDefinition(TestProject.MULTI_FLAVOR_WITH_FILTERING),
      // Skip V1 and V2 comparison for namespace project. The support for namespace in V2 is stricter since ag/16005984.
      // More info b/111168382.
      IdeModelSnapshotComparisonTestDefinition(TestProject.NAMESPACES, skipV1toV2Comparison = true),
      IdeModelSnapshotComparisonTestDefinition(TestProject.INCLUDE_FROM_LIB),
      IdeModelSnapshotComparisonTestDefinition(
        TestProject.LOCAL_AARS_AS_MODULES,
        v1toV2PropertiesToSkip = setOf("provided")
      ), // Skip __wrapped_aars__.
      IdeModelSnapshotComparisonTestDefinition(TestProject.BASIC),
      IdeModelSnapshotComparisonTestDefinition(TestProject.PRIVACY_SANDBOX_SDK, skipV1toV2Comparison = true)
    )
  }


  override val name: String = testProject.projectName

  override fun toString(): String = testProject.projectName

  override fun isCompatible(): Boolean {
    return isCompatibleWith(agpVersion)
  }

  override fun runTest(root: File, project: Project) {
    val v2snapshots = SnapshotContext(testProject.projectName, agpVersion, IDE_MODEL_SNAPSHOT_DIR)
    val dump = project.saveAndDump(mapOf("ROOT" to root)) { project, projectDumper ->
      projectDumper.dumpAndroidIdeModel(
        project,
        kotlinModels = { CapturePlatformModelsProjectResolverExtension.getKotlinModel(it) },
        kaptModels = { CapturePlatformModelsProjectResolverExtension.getKaptModel(it) },
        mppModels = {CapturePlatformModelsProjectResolverExtension.getMppModel(it) },
        externalProjects = { if (agpVersion >= AGP_41) CapturePlatformModelsProjectResolverExtension.getExternalProjectModel(it) else null }
      )
    }
    v2snapshots.assertIsEqualToSnapshot(dump)
    // Do not remove `return`.
    return when (agpVersion) {
      AGP_80 -> testV1vsV2(AGP_80_V1, AGP_80)
      AGP_72 -> testV1vsV2(AGP_72_V1, AGP_72)
      // Do not replace with when.
      AGP_32 -> Unit
      AGP_35 -> Unit
      AGP_40 -> Unit
      AGP_41 -> Unit
      AGP_42 -> Unit
      AGP_70 -> Unit
      AGP_71 -> Unit
      AGP_72_V1 -> Unit
      AGP_73 -> Unit
      AGP_80_V1 -> Unit
    }
  }

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): TestDef {
    return copy(agpVersion = agpVersion)
  }

  private fun testV1vsV2(
    v1Version: AgpVersionSoftwareEnvironmentDescriptor,
    v2Version: AgpVersionSoftwareEnvironmentDescriptor
  ) {
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

    val v1snapshots = SnapshotContext(testProject.projectName, v1Version, IDE_MODEL_SNAPSHOT_DIR)
    val (_, expectedTextV1) = v1snapshots.getAndMaybeUpdateSnapshot("", "", doNotUpdate = true)

    val v2snapshots = SnapshotContext(testProject.projectName, v2Version, IDE_MODEL_SNAPSHOT_DIR)
    val (_, expectedTextV2) = v2snapshots.getAndMaybeUpdateSnapshot("", "", doNotUpdate = true)


    val expectedTextV2Filtered = expectedTextV2.filterOutProperties()
    val expectedTextV1Filtered = expectedTextV1.filterOutProperties()

    Truth.assertThat(expectedTextV2Filtered).isEqualTo(expectedTextV1Filtered)
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

private const val IDE_MODEL_SNAPSHOT_DIR = "tools/adt/idea/android/testData/snapshots/ideModels"
