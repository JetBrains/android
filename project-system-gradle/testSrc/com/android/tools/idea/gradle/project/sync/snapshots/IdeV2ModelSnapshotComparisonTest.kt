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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.getAndMaybeUpdateSnapshot
import com.android.tools.idea.testing.nameProperties
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.junit.Assume
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
 * target as "//tools/adt/idea/android:intellij.android.core.tests_tests__all --test_filter=IdeV2ModelSnapshotComparisonTest".
 */
@RunsInEdt
@RunWith(Parameterized::class)
class IdeV2ModelSnapshotComparisonTest : GradleIntegrationTest, SnapshotComparisonTest {
  data class TestProject(
    val template: String,
    val pathToOpen: String = "",
    val skipV1toV2Comparison: Boolean = false,
    val v1toV2PropertiesToSkip: Set<String> = emptySet(),
    val testName: String? = null,
    val patch: (projectRoot: File) -> Unit = {}
  ) {
    override fun toString(): String = "${template.removePrefix("projects/")}$pathToOpen${if (testName == null) "" else " - $testName"}"
  }
  @JvmField
  @Parameterized.Parameter
  var testProjectName: TestProject? = null
  companion object {
    @Suppress("unused")
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> = listOf(
      TestProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION),
      TestProject(
        TestProjectToSnapshotPaths.SIMPLE_APPLICATION, testName = "additionalGradleSourceSets", skipV1toV2Comparison = true) { root ->
        val buildFile = root.resolve("app").resolve("build.gradle")
        buildFile.writeText(buildFile.readText() + """
          sourceSets {
            test.resources.srcDirs += 'src/test/resources'
          }
        """.trimIndent())
      },
      TestProject(TestProjectToSnapshotPaths.WITH_GRADLE_METADATA),
      TestProject(TestProjectToSnapshotPaths.BASIC_CMAKE_APP),
      TestProject(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY),
      TestProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD) { projectRoot ->
        truncateForV2(projectRoot.resolve("settings.gradle"))
      },
      TestProject(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS, "/application"),
      TestProject(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES, skipV1toV2Comparison = true),
      TestProject(TestProjectToSnapshotPaths.LINKED, "/firstapp"),
      TestProject(TestProjectToSnapshotPaths.KOTLIN_KAPT),
      TestProject(TestProjectToSnapshotPaths.LINT_CUSTOM_CHECKS),
      TestProject(TestProjectToSnapshotPaths.TEST_FIXTURES, skipV1toV2Comparison = true),
      // Ignore comparing the variant name for module dependencies because this is not always provided by V1 models.
      TestProject(
        TestProjectToSnapshotPaths.TEST_ONLY_MODULE,
        v1toV2PropertiesToSkip =
        setOf(
          "moduleDependencies",
          "moduleDependencies/target",
          "moduleDependencies/target/buildId",
          "moduleDependencies/target/projectPath",
          "moduleDependencies/target/sourceSet"
        )
      ),
      TestProject(TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM),
      TestProject(TestProjectToSnapshotPaths.MULTI_FLAVOR),
      // Skip V1 and V2 comparison for namespace project. The support for namespace in V2 is stricter since ag/16005984. more info b/111168382.
      TestProject(TestProjectToSnapshotPaths.NAMESPACES, skipV1toV2Comparison = true),
      TestProject(TestProjectToSnapshotPaths.INCLUDE_FROM_LIB),
      TestProject(TestProjectToSnapshotPaths.LOCAL_AARS_AS_MODULES, v1toV2PropertiesToSkip = setOf("provided")), // Skip __wrapped_aars__.
      TestProject(TestProjectToSnapshotPaths.BASIC)
    )
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()
  override fun getName(): String = testProjectName?.testName ?: testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))
  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/v2IdeModels"

  @Test
  fun testIdeModels() {
    val projectName = testProjectName ?: error("unit test parameter not initialized")
    val root = prepareGradleProject(
      projectName.template,
      "project"
    )
    projectName.patch(root)
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
  }

  @Test
  fun testV1vsV2() {
    Assume.assumeFalse(testProjectName!!.skipV1toV2Comparison)
    val adjustedTestName = this@IdeV2ModelSnapshotComparisonTest.getName().replace("V1vsV2", "ideModels")
    val v1snapshots = object : SnapshotComparisonTest by this {
      override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/ideModels"
      override fun getName(): String = adjustedTestName
    }
    val (_, expectedTextV1) = v1snapshots.getAndMaybeUpdateSnapshot("NewAgp_", "", doNotUpdate = true)

    val v2snapshots = object : SnapshotComparisonTest by this {
      override fun getName(): String = adjustedTestName
    }
    val (_, expectedTextV2) = v2snapshots.getAndMaybeUpdateSnapshot("", "", doNotUpdate = true)


    val expectedTextV2Filtered = expectedTextV2.filterOutProperties()
    val expectedTextV1Filtered = expectedTextV1.filterOutProperties()

    Truth.assertThat(expectedTextV2Filtered).isEqualTo(expectedTextV1Filtered)
  }

  private fun String.filterOutProperties(): String =
    this
      .splitToSequence('\n')
      .nameProperties()
      .filter { (property, line) ->
        !PROPERTIES_TO_SKIP.any { property.endsWith(it) } &&
        !ENTITIES_TO_SKIP.any { property.contains(it) } &&
        !testProjectName!!.v1toV2PropertiesToSkip.any { property.endsWith(it) }
      }
      .filter { (property, line) -> !VALUES_TO_SUPPRESS.any { property.endsWith(it.key) and it.value.any { value -> line.contains(value) } } }
      .map { it.first + " <> " + it.second }
      .joinToString(separator = "\n")

}

private fun Sequence<String>.nameProperties() = nameProperties(this)

/**
 * we skip:
 * [IdeAndroidLibrary.lintJar] because in V2 we do check that the jar exists before populating the property.
 * [com.android.tools.idea.gradle.model.IdeVariant.deprecatedPreMergedApplicationId] as not present in V2
 * [com.android.tools.idea.gradle.model.IdeVariant.deprecatedPreMergedTestApplicationId] as not present in V2
 * [ModelSyncFile] as these are not present in V1.
 * [runetimeClasspath] as it is not available in V1.
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

private fun truncateForV2(settingsFile: File) {
  val patchedText = settingsFile.readLines().takeWhile { !it.contains("//-v2:truncate-from-here") }.joinToString("\n")
  assertThat(patchedText.trim()).isNotEqualTo(settingsFile.readText().trim())
  settingsFile.writeText(patchedText)
}
