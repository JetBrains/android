/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.AssumeUtil.assumeNotWindows
import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.FileSubject.file
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.ModelVersion
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.utils.FileUtils
import com.android.utils.FileUtils.writeToFile
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import org.jetbrains.annotations.SystemIndependent
import java.io.File
import java.nio.file.Files

/**
 * Defines test projects used in [SyncedProjectTest].
 *
 * When adding a new entry to this file add a new test method to [SyncedProjectTest].
 */
enum class TestProject(
  private val template: String,
  private val pathToOpen: String = "",
  private val testName: String? = null,
  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = { true },
  private val setup: () -> () -> Unit = { {} },
  private val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit = {},
  private val expectedSyncIssues: Set<Int> = emptySet()
) : TestProjectDefinition {
  APP_WITH_ML_MODELS(TestProjectToSnapshotPaths.APP_WITH_ML_MODELS),
  APP_WITH_BUILDSRC(TestProjectToSnapshotPaths.APP_WITH_BUILDSRC),
  COMPATIBILITY_TESTS_AS_36(TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36, patch = { updateProjectJdk(it) }),
  COMPATIBILITY_TESTS_AS_36_NO_IML(TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36_NO_IML, patch = { updateProjectJdk(it) }),
  SIMPLE_APPLICATION(TestProjectToSnapshotPaths.SIMPLE_APPLICATION),
  SIMPLE_APPLICATION_NO_PARALLEL_SYNC(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "noParallelSync",
    setup =
    fun(): () -> Unit {
      val oldValue = GradleExperimentalSettings.getInstance().ENABLE_PARALLEL_SYNC

      GradleExperimentalSettings.getInstance().ENABLE_PARALLEL_SYNC = false

      return fun() {
        GradleExperimentalSettings.getInstance().ENABLE_PARALLEL_SYNC = oldValue
      }
    },
  ),
  SIMPLE_APPLICATION_VIA_SYMLINK(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "viaSymLink",
    patch = { root ->
      assumeNotWindows()
      val linkSourcePath = root.parentFile.resolve(root.name + "_sm_src").toPath()
      Files.move(root.toPath(), linkSourcePath)
      Files.createSymbolicLink(root.toPath(), linkSourcePath)
      VfsUtil.markDirtyAndRefresh(false, true, true, root)
    }
  ),
  SIMPLE_APPLICATION_APP_VIA_SYMLINK(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "appViaSymLink",
    patch = { root ->
      assumeNotWindows()
      val app = root.resolve("app").toPath()
      val linkSourcePath = root.resolve("app_sm_src").toPath()
      Files.move(app, linkSourcePath)
      Files.createSymbolicLink(app, linkSourcePath)
      VfsUtil.markDirtyAndRefresh(false, true, true, root)
    }
  ),
  SIMPLE_APPLICATION_WITH_ADDITIONAL_GRADLE_SOURCE_SETS(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "additionalGradleSourceSets",
    patch = { root ->
      val buildFile = root.resolve("app").resolve("build.gradle")
      buildFile.writeText(
        buildFile.readText() + """
          sourceSets {
            test.resources.srcDirs += 'src/test/resources'
          }
        """.trimIndent()
      )
    }
  ),
  SIMPLE_APPLICATION_NOT_AT_ROOT(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "gradleNotAtRoot",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { moveGradleRootUnderGradleProjectDirectory(it) }
  ),
  SIMPLE_APPLICATION_MULTIPLE_ROOTS(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "multipleGradleRoots",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { moveGradleRootUnderGradleProjectDirectory(it, makeSecondCopy = true) }
  ),
  SIMPLE_APPLICATION_WITH_UNNAMED_DIMENSION(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "withUnnamedDimension",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { root ->
      root.resolve("app/build.gradle").replaceContent {
        it + """
          android.productFlavors {
           example {
           }
          }
         """
      }
    },
    expectedSyncIssues = setOf(SyncIssue.TYPE_UNNAMED_FLAVOR_DIMENSION)
  ),
  WITH_GRADLE_METADATA(TestProjectToSnapshotPaths.WITH_GRADLE_METADATA),
  BASIC_CMAKE_APP(TestProjectToSnapshotPaths.BASIC_CMAKE_APP),
  PSD_SAMPLE_GROOVY(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY),
  COMPOSITE_BUILD(
    TestProjectToSnapshotPaths.COMPOSITE_BUILD,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_70 },
    patch = { projectRoot ->
      if (modelVersion == ModelVersion.V2) {
        truncateForV2(projectRoot.resolve("settings.gradle"))
      }
    }),
  NON_STANDARD_SOURCE_SETS(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS, "/application"),
  NON_STANDARD_SOURCE_SET_DEPENDENCIES(
    TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES,
    isCompatibleWith = { it.modelVersion == ModelVersion.V2 }
  ),
  NON_STANDARD_SOURCE_SET_DEPENDENCIES_MANUAL_TEST_FIXTURES_WORKAROUND(
    TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES,
    testName = "manualTestFixturesWorkaround",
    isCompatibleWith = { it.modelVersion == ModelVersion.V2 },
    patch = {
      it.resolve("app/build.gradle")
        .replaceInContent("androidTestImplementation project(':lib')", "// androidTestImplementation project(':lib')")
    }
  ),
  NON_STANDARD_SOURCE_SET_DEPENDENCIES_HIERARCHICAL(
    TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES,
    testName = "hierarchical",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, enableHierarchicalSupport = true) }
  ),
  LINKED(TestProjectToSnapshotPaths.LINKED, "/firstapp"),
  KOTLIN_KAPT(TestProjectToSnapshotPaths.KOTLIN_KAPT),
  LINT_CUSTOM_CHECKS(
    TestProjectToSnapshotPaths.LINT_CUSTOM_CHECKS,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_71 }
  ),
  TEST_FIXTURES(
    TestProjectToSnapshotPaths.TEST_FIXTURES,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_72 }
  ),
  TEST_ONLY_MODULE(TestProjectToSnapshotPaths.TEST_ONLY_MODULE),
  KOTLIN_MULTIPLATFORM(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_70 }
  ),
  KOTLIN_MULTIPLATFORM_HIERARCHICAL(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "hierarchical",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, enableHierarchicalSupport = true) }
  ),
  KOTLIN_MULTIPLATFORM_HIERARCHICAL_WITHJS(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "hierarchical_withjs",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, enableHierarchicalSupport = true, addJsModule = true) }
  ),
  KOTLIN_MULTIPLATFORM_JVM(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "jvm",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, enableHierarchicalSupport = false, addJvmTo = listOf("module2")) }
  ),
  KOTLIN_MULTIPLATFORM_JVM_HIERARCHICAL(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "jvm_hierarchical",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, enableHierarchicalSupport = true, addJvmTo = listOf("module2")) }
  ),
  KOTLIN_MULTIPLATFORM_JVM_HIERARCHICAL_KMPAPP(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "jvm_hierarchical_kmpapp",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, enableHierarchicalSupport = true, convertAppToKmp = true, addJvmTo = listOf("app", "module2")) }
  ),
  KOTLIN_MULTIPLATFORM_JVM_HIERARCHICAL_KMPAPP_WITHINTERMEDIATE(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "jvm_hierarchical_kmpapp_withintermediate",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = {
      patchMppProject(
        it,
        enableHierarchicalSupport = true,
        convertAppToKmp = true,
        addJvmTo = listOf("app", "module2"),
        addIntermediateTo = listOf("module2")
      )
    }
  ),
  MULTI_FLAVOR(TestProjectToSnapshotPaths.MULTI_FLAVOR),
  MULTI_FLAVOR_WITH_FILTERING(
    TestProjectToSnapshotPaths.MULTI_FLAVOR,
    testName = "_withFiltering",
    patch = { projectRoot ->
      projectRoot.resolve("app").resolve("build.gradle").replaceContent { content ->
        content
          .replace(" implementation", "// implementation")
          .replace(" androidTestImplementation", "// androidTestImplementation") +
        """
              android.variantFilter { variant ->
                  variant.setIgnore(!variant.name.startsWith("firstAbcSecondAbc"))
              }
        """
      }
    }
  ),
  NAMESPACES(TestProjectToSnapshotPaths.NAMESPACES),
  INCLUDE_FROM_LIB(TestProjectToSnapshotPaths.INCLUDE_FROM_LIB),
  LOCAL_AARS_AS_MODULES(TestProjectToSnapshotPaths.LOCAL_AARS_AS_MODULES),
  BASIC(TestProjectToSnapshotPaths.BASIC),
  BASIC_WITH_EMPTY_SETTINGS_FILE(
    TestProjectToSnapshotPaths.BASIC,
    testName = "basicWithEmptySettingsFile",
    patch = { projectRootPath ->
      createEmptyGradleSettingsFile(projectRootPath)
    }),
  MAIN_IN_ROOT(TestProjectToSnapshotPaths.MAIN_IN_ROOT),
  NESTED_MODULE(TestProjectToSnapshotPaths.NESTED_MODULE),
  TRANSITIVE_DEPENDENCIES(TestProjectToSnapshotPaths.TRANSITIVE_DEPENDENCIES),
  TRANSITIVE_DEPENDENCIES_NO_TARGET_SDK_IN_LIBS(
    TestProjectToSnapshotPaths.TRANSITIVE_DEPENDENCIES,
    testName = "_no_target_sdk_in_libs",
    patch = { projectRootPath ->
      fun patch(content: String): String {
        return content
          .replace("targetSdkVersion", "// targetSdkVersion")
      }

      projectRootPath.resolve("library1").resolve("build.gradle").replaceContent(::patch)
      projectRootPath.resolve("library2").resolve("build.gradle").replaceContent(::patch)
    }
  ),
  KOTLIN_GRADLE_DSL(TestProjectToSnapshotPaths.KOTLIN_GRADLE_DSL),
  NEW_SYNC_KOTLIN_TEST(TestProjectToSnapshotPaths.NEW_SYNC_KOTLIN_TEST),
  TWO_JARS(TestProjectToSnapshotPaths.TWO_JARS),
  API_DEPENDENCY(TestProjectToSnapshotPaths.API_DEPENDENCY),
  NAVIGATOR_PACKAGEVIEW_COMMONROOTS(TestProjectToSnapshotPaths.NAVIGATOR_PACKAGEVIEW_COMMONROOTS),
  NAVIGATOR_PACKAGEVIEW_SIMPLE(TestProjectToSnapshotPaths.NAVIGATOR_PACKAGEVIEW_SIMPLE),
  SIMPLE_APPLICATION_VERSION_CATALOG(TestProjectToSnapshotPaths.SIMPLE_APPLICATION_VERSION_CATALOG),
  CUSTOM_SOURCE_TYPE(TestProjectToSnapshotPaths.CUSTOM_SOURCE_TYPE),
  LIGHT_SYNC_REFERENCE(TestProjectToSnapshotPaths.LIGHT_SYNC_REFERENCE),
  PURE_JAVA_PROJECT(TestProjectToSnapshotPaths.PURE_JAVA_PROJECT),
  BUILDSRC_WITH_COMPOSITE(TestProjectToSnapshotPaths.BUILDSRC_WITH_COMPOSITE),
  PRIVACY_SANDBOX_SDK(
    TestProjectToSnapshotPaths.PRIVACY_SANDBOX_SDK,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT },
  )
  ;

  val projectName: String get() = "${template.removePrefix("projects/")}$pathToOpen${if (testName == null) "" else " - $testName"}"

  val templateAbsolutePath: File get() = resolveTestDataPath(template)
  val additionalRepositories: Collection<File> get() = getAdditionalRepos()

  private fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  private fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))

  private fun resolveTestDataPath(testDataPath: @SystemIndependent String): File {
    val testDataDirectory = TestUtils.getWorkspaceRoot()
      .resolve(FileUtil.toSystemDependentName(getTestDataDirectoryWorkspaceRelativePath()))
    return testDataDirectory.resolve(FileUtil.toSystemDependentName(testDataPath)).toFile()
  }

  private fun defaultOpenPreparedProjectOptions(): OpenPreparedProjectOptions {
    return OpenPreparedProjectOptions(expectedSyncIssues = expectedSyncIssues)
  }

  override fun preparedTestProject(
    integrationTestEnvironment: IntegrationTestEnvironment,
    name: String,
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor
  ): PreparedTestProject {
    val root = integrationTestEnvironment.prepareGradleProject(
      templateAbsolutePath,
      additionalRepositories,
      name,
      agpVersion,
      ndkVersion = SdkConstants.NDK_DEFAULT_VERSION
    )
    patch(agpVersion, root)

    return object : PreparedTestProject {
      override val root: File = root
      override fun <T> open(updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions, body: (Project) -> T): T {
        val tearDown = setup()
        try {
          return integrationTestEnvironment.openPreparedProject(
            name = "$name$pathToOpen",
            options = updateOptions(defaultOpenPreparedProjectOptions())
          ) { project ->
            invokeAndWaitIfNeeded {
              AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
            }
            body(project)
          }
        } finally {
          tearDown()
        }
      }
    }
  }
}

class SnapshotContext(
  projectName: String,
  agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
  workspace: String,
) : SnapshotComparisonTest {

  private val name: String =
    "$projectName${agpVersion.agpSuffix()}${agpVersion.gradleSuffix()}${agpVersion.modelVersion}"

  override val snapshotDirectoryWorkspaceRelativePath: String = workspace
  override fun getName(): String = name
}

private fun File.replaceContent(change: (String) -> String) {
  writeText(
    readText().let {
      val result = change(it)
      if (it == result) error("No replacements made")
      result
    }
  )
}

private fun File.replaceInContent(oldValue: String, newValue: String) {
  replaceContent { it.replace(oldValue, newValue) }
}

private fun truncateForV2(settingsFile: File) {
  val patchedText = settingsFile.readLines().takeWhile { !it.contains("//-v2:truncate-from-here") }.joinToString("\n")
  Truth.assertThat(patchedText.trim()).isNotEqualTo(settingsFile.readText().trim())
  settingsFile.writeText(patchedText)
}

private fun moveGradleRootUnderGradleProjectDirectory(root: File, makeSecondCopy: Boolean = false) {
  val testJdkName = IdeSdks.getInstance().jdk?.name ?: error("No JDK in test")
  val newRoot = root.resolve(if (makeSecondCopy) "gradle_project_1" else "gradle_project")
  val newRoot2 = root.resolve("gradle_project_2")
  val tempRoot = File(root.path + "_tmp")
  val ideaDirectory = root.resolve(".idea")
  val gradleXml = ideaDirectory.resolve("gradle.xml")
  val miscXml = ideaDirectory.resolve("misc.xml")
  Files.move(root.toPath(), tempRoot.toPath())
  Files.createDirectory(root.toPath())
  Files.move(tempRoot.toPath(), newRoot.toPath())
  if (makeSecondCopy) {
    FileUtils.copyDirectory(newRoot, newRoot2)
    newRoot2
      .resolve("settings.gradle")
      .replaceContent { "rootProject.name = 'gradle_project_name'\n$it" } // Give it a name not matching the directory name.
  }
  Files.createDirectory(ideaDirectory.toPath())

  fun gradleSettingsFor(rootName: String): String {
    return """
      <GradleProjectSettings>
        <option name="testRunner" value="GRADLE" />
        <option name="distributionType" value="DEFAULT_WRAPPED" />
        <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}/$rootName" />
      </GradleProjectSettings>
    """
  }

  gradleXml.writeText(
    """
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleMigrationSettings" migrationVersion="1" />
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
        ${gradleSettingsFor(newRoot.name)}
        ${if (makeSecondCopy) gradleSettingsFor(newRoot2.name) else ""}
    </option>
  </component>
</project>        
    """.trim()
  )

  miscXml.writeText(
    """
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
<component name="ProjectRootManager" version="2" project-jdk-name="$testJdkName" project-jdk-type="JavaSDK" />
</project>
    """.trim()
  )
}

private fun patchMppProject(
  projectRoot: File,
  enableHierarchicalSupport: Boolean,
  convertAppToKmp: Boolean = false,
  addJvmTo: List<String> = emptyList(),
  addIntermediateTo: List<String> = emptyList(),
  addJsModule: Boolean = false
) {
  if (enableHierarchicalSupport) {
    projectRoot.resolve("gradle.properties").replaceInContent(
      "kotlin.mpp.hierarchicalStructureSupport=false",
      "kotlin.mpp.hierarchicalStructureSupport=true"
    )
  }
  if (convertAppToKmp) {
    projectRoot.resolve("app").resolve("build.gradle").replaceInContent(
      """
        plugins {
            id 'com.android.application'
            id 'kotlin-android'
        }
      """.trimIndent(),
      """
        plugins {
            id 'com.android.application'
            id 'kotlin-multiplatform'
        }
        kotlin {
            android()
        }
     """.trimIndent(),
    )
  }
  for (module in addJvmTo) {
    projectRoot.resolve(module).resolve("build.gradle").replaceInContent(
      "android()",
      "android()\njvm()"
    )
  }
  for (module in addIntermediateTo) {
    projectRoot.resolve(module).resolve("build.gradle").replaceInContent(
      """
        |sourceSets {
      """.trimMargin(),
      """
        |sourceSets {
        |  create("jvmAndAndroid") {
        |    dependsOn(commonMain)
        |    androidMain.dependsOn(it)
        |    jvmMain.dependsOn(it)
        |  }
      """.trimMargin()
    )
  }
  if (addJsModule) {
    projectRoot.resolve("settings.gradle")
      .replaceInContent("//include ':jsModule'", "include ':jsModule'")
    // "org.jetbrains.kotlin.js" conflicts with "clean" task.
    projectRoot.resolve("build.gradle")
      .replaceInContent("task clean(type: Delete)", "task clean1(type: Delete)")
  }
}

private fun AgpVersionSoftwareEnvironmentDescriptor.updateProjectJdk(projectRoot: File) {
  val jdk = IdeSdks.getInstance().jdk ?: error("${SyncedProjectTest::class} requires a valid JDK")
  val miscXml = projectRoot.resolve(".idea").resolve("misc.xml")
  miscXml.writeText(miscXml.readText().replace("""project-jdk-name="1.8"""", """project-jdk-name="${jdk.name}""""))
}

private fun createEmptyGradleSettingsFile(projectRootPath: File) {
  val settingsFilePath = File(projectRootPath, FN_SETTINGS_GRADLE)
  assertThat(FileUtil.delete(settingsFilePath)).isTrue()
  writeToFile(settingsFilePath, " ")
  assertAbout(file()).that(settingsFilePath).isFile()
  refreshProjectFiles()
}

private fun AgpVersionSoftwareEnvironmentDescriptor.agpSuffix(): String = when (this) {
  AgpVersionSoftwareEnvironmentDescriptor.AGP_80 -> "_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_80_V1 -> "_NewAgp_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_32 -> "_Agp_3.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_35 -> "_Agp_3.5_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_40 -> "_Agp_4.0_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_41 -> "_Agp_4.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_42 -> "_Agp_4.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_70 -> "_Agp_7.0_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_71 -> "_Agp_7.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_72_V1 -> "_Agp_7.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_72 -> "_Agp_7.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_73 -> "_Agp_7.3_"
}

private fun AgpVersionSoftwareEnvironmentDescriptor.gradleSuffix(): String {
  return gradleVersion?.let { "Gradle_${it}_" }.orEmpty()
}

