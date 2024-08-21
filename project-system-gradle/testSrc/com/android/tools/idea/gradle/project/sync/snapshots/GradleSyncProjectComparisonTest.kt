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

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_33
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_42
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.ModuleModelBuilder
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.SnapshotContext
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.getAndMaybeUpdateSnapshot
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

/**
 * Snapshot tests for 'Gradle Sync'.
 *
 * These tests compare the results of sync by converting the resulting project to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and comparing them to pre-recorded golden sync
 * results.
 *
 * The pre-recorded sync results can be found in testData/syncedProjectSnapshots/ *.txt files.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest] and if running from the command-line use
 * target as "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.project.sync.snapshots".
 */
data class ProjectStructureSnapshotTestDef(
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AGP_CURRENT,
  private val roots: Map<String, File> = emptyMap(),
  private val compatibleWith: Set<AgpVersionSoftwareEnvironmentDescriptor> = setOf(AGP_CURRENT)
) : SyncedProjectTestDef {

  override val name: String = testProject.projectName

  override fun toString(): String = testProject.projectName

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun isCompatible(): Boolean {
    return agpVersion in compatibleWith
  }

  override fun runTest(root: File, project: Project) {
    val text = project.saveAndDump(additionalRoots = roots.mapValues { root.resolve(it.value) })
    SnapshotContext(testProject.projectName, agpVersion, PROJECT_STRUCTURE_SNAPSHOT_DIR).assertIsEqualToSnapshot(text)
  }

  companion object {
    val tests: List<ProjectStructureSnapshotTestDef> = listOf(
      ProjectStructureSnapshotTestDef(TestProject.SIMPLE_APPLICATION, compatibleWith = setOf(AGP_33, AGP_CURRENT)),
      ProjectStructureSnapshotTestDef(TestProject.SIMPLE_APPLICATION_VIA_SYMLINK, compatibleWith = setOf(AGP_33, AGP_CURRENT)),
      ProjectStructureSnapshotTestDef(TestProject.SIMPLE_APPLICATION_APP_VIA_SYMLINK, compatibleWith = setOf(AGP_33, AGP_CURRENT)),
      ProjectStructureSnapshotTestDef(TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT),
      ProjectStructureSnapshotTestDef(TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS),
      ProjectStructureSnapshotTestDef(TestProject.SIMPLE_APPLICATION_WITH_UNNAMED_DIMENSION),
      ProjectStructureSnapshotTestDef(TestProject.SIMPLE_APPLICATION_WITH_ANDROID_CAR),
      ProjectStructureSnapshotTestDef(TestProject.SIMPLE_APPLICATION_WITH_SCREENSHOT_TEST),
      ProjectStructureSnapshotTestDef(TestProject.PURE_JAVA_PROJECT),
      ProjectStructureSnapshotTestDef(TestProject.MAIN_IN_ROOT),
      ProjectStructureSnapshotTestDef(TestProject.NESTED_MODULE),
      ProjectStructureSnapshotTestDef(TestProject.BASIC_WITH_EMPTY_SETTINGS_FILE),
      ProjectStructureSnapshotTestDef(TestProject.TRANSITIVE_DEPENDENCIES),
      ProjectStructureSnapshotTestDef(TestProject.WITH_GRADLE_METADATA),
      ProjectStructureSnapshotTestDef(TestProject.TEST_FIXTURES),
      ProjectStructureSnapshotTestDef(TestProject.TEST_ONLY_MODULE),
      ProjectStructureSnapshotTestDef(TestProject.APP_WITH_ML_MODELS),
      ProjectStructureSnapshotTestDef(TestProject.MULTI_FLAVOR),
      ProjectStructureSnapshotTestDef(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES),
      ProjectStructureSnapshotTestDef(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES_MANUAL_TEST_FIXTURES_WORKAROUND),
      ProjectStructureSnapshotTestDef(TestProject.KOTLIN_GRADLE_DSL),
      ProjectStructureSnapshotTestDef(TestProject.NEW_SYNC_KOTLIN_TEST),
      ProjectStructureSnapshotTestDef(TestProject.PSD_SAMPLE_GROOVY),
      ProjectStructureSnapshotTestDef(TestProject.TWO_JARS),
      ProjectStructureSnapshotTestDef(TestProject.COMPOSITE_BUILD),
      ProjectStructureSnapshotTestDef(TestProject.APP_WITH_BUILDSRC),
      ProjectStructureSnapshotTestDef(TestProject.APP_WITH_BUILDSRC_AND_SETTINGS_PLUGIN),
      ProjectStructureSnapshotTestDef(TestProject.KOTLIN_MULTIPLATFORM),
      ProjectStructureSnapshotTestDef(TestProject.KOTLIN_MULTIPLATFORM_WITHJS),
      ProjectStructureSnapshotTestDef(TestProject.KOTLIN_MULTIPLATFORM_IOS),
      ProjectStructureSnapshotTestDef(TestProject.KOTLIN_MULTIPLATFORM_JVM),
      ProjectStructureSnapshotTestDef(TestProject.KOTLIN_MULTIPLATFORM_JVM_KMPAPP),
      ProjectStructureSnapshotTestDef(TestProject.KOTLIN_MULTIPLATFORM_JVM_KMPAPP_WITHINTERMEDIATE),
      ProjectStructureSnapshotTestDef(TestProject.KOTLIN_MULTIPLATFORM_MULTIPLE_SOURCE_SET_PER_ANDROID_COMPILATION),
      ProjectStructureSnapshotTestDef(TestProject.ANDROID_KOTLIN_MULTIPLATFORM),
      ProjectStructureSnapshotTestDef(TestProject.KOTLIN_KAPT),
      ProjectStructureSnapshotTestDef(TestProject.COMPATIBILITY_TESTS_AS_36),
      ProjectStructureSnapshotTestDef(TestProject.COMPATIBILITY_TESTS_AS_36_NO_IML),
      ProjectStructureSnapshotTestDef(TestProject.API_DEPENDENCY),
      ProjectStructureSnapshotTestDef(TestProject.LIGHT_SYNC_REFERENCE),
      ProjectStructureSnapshotTestDef(
        TestProject.NON_STANDARD_SOURCE_SETS,
        roots = mapOf(
          "EXTERNAL_SOURCE_SET" to File("externalRoot"),
          "EXTERNAL_MANIFEST" to File("externalManifest")
        )
      ),
      ProjectStructureSnapshotTestDef(TestProject.BUILDSRC_WITH_COMPOSITE, compatibleWith = setOf(AGP_42, AGP_CURRENT)),
      ProjectStructureSnapshotTestDef(TestProject.PRIVACY_SANDBOX_SDK),
      ProjectStructureSnapshotTestDef(TestProject.APP_WITH_BUILD_FEATURES_ENABLED),
      ProjectStructureSnapshotTestDef(TestProject.DEPENDENT_MODULES_ONLY_APP_RUNTIME, compatibleWith = setOf(AGP_CURRENT)),
      ProjectStructureSnapshotTestDef(TestProject.BUILD_CONFIG_AS_BYTECODE_ENABLED),
      ProjectStructureSnapshotTestDef(TestProject.TEST_STATIC_DIR)
    )
  }
}

private object LightGradleSyncReferenceTestProject: LightGradleSyncTestProject {
  override val templateProject: TemplateBasedTestProject = TestProject.LIGHT_SYNC_REFERENCE
  override val modelBuilders: List<ModuleModelBuilder> = listOf(
    JavaModuleModelBuilder.rootModuleBuilder.copy(
      groupId = "",
      version = "unspecified",
    ),
    AndroidModuleModelBuilder(
      gradlePath = ":app",
      groupId = "reference",
      version = "unspecified",
      selectedBuildVariant = "debug",
      projectBuilder = AndroidProjectBuilder(
        androidModuleDependencyList = {
          listOf(
            AndroidModuleDependency(
              ":androidlibrary",
              "debug"
            )
          )
        },
        namespace = { "com.example.skeleton" },
        includeShadersSources = { true }
      ).build(),
    ),
    AndroidModuleModelBuilder(
      gradlePath = ":androidlibrary",
      groupId = "reference",
      version = "unspecified",
      selectedBuildVariant = "debug",
      projectBuilder = AndroidProjectBuilder(
        projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
        androidModuleDependencyList = { listOf(AndroidModuleDependency(":javalib", null)) },
        namespace = { "com.example.androidlibrary" },
        includeShadersSources = { true }
      ).build()
    ),
    JavaModuleModelBuilder(
      ":javalib",
      groupId = "reference",
      version = "unspecified",
    )
  )
}

@RunsInEdt
class LightSyncReferenceTest : SnapshotComparisonTest {
  @get:Rule
  var testName = TestName()

  @get:Rule
  val projectRule = AndroidProjectRule.testProject(LightGradleSyncReferenceTestProject).named("reference")

  override fun getName(): String = testName.methodName
  override val snapshotDirectoryWorkspaceRelativePath: String = PROJECT_STRUCTURE_SNAPSHOT_DIR

  @Test
  fun testLightSyncActual() {
    val dump = projectRule.project.saveAndDump()
    assertIsEqualToSnapshot(dump)
  }

  @Test
  fun compareResults() {
    val expectedSnapshot = object : SnapshotComparisonTest by this {
      override fun getName(): String = "LightSyncReference_V2"
    }
    val (_, expectedSnapshotTest) = expectedSnapshot.getAndMaybeUpdateSnapshot("", "", doNotUpdate = true)

    val actualSnapshot = object : SnapshotComparisonTest by this {
      override fun getName(): String = "testLightSyncActual"
    }
    val (_, actualSnapshotTest) = actualSnapshot.getAndMaybeUpdateSnapshot("", "", doNotUpdate = true)

    assertThat(actualSnapshotTest.filterOutProperties()).isEqualTo(expectedSnapshotTest.filterOutProperties())
  }
}

private fun String.filterOutProperties(): String =
  this
    .splitToSequence('\n')
    .nameProperties()
    .filter { (property, line) ->
      !PROPERTIES_TO_SKIP_BY_PREFIXES.any { property.startsWith(it) }
    }
    .map { it.first + " >> " + it.second }
    .joinToString(separator = "\n")

private fun Sequence<String>.nameProperties() = com.android.tools.idea.testing.nameProperties(this)

private val PROPERTIES_TO_SKIP_BY_PREFIXES = setOf(
  "PROJECT/BUILD_TASKS",
  "PROJECT/LIBRARY_TABLE",
  "PROJECT/MODULE/BUILD_TASKS",
  "PROJECT/MODULE/Classes",
  "PROJECT/MODULE/COMPILER_MODULE_EXTENSION",
  "PROJECT/MODULE/LIBRARY",
  "PROJECT/MODULE/TEST_MODULE_PROPERTIES",
  "PROJECT/RUN_CONFIGURATION"
)

private const val PROJECT_STRUCTURE_SNAPSHOT_DIR = "tools/adt/idea/android/testData/snapshots/syncedProjects"
