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

import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.AssumeUtil.assumeNotWindows
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.ModelVersion
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.resolve
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import java.io.File
import java.nio.file.Files

/**
 * Defines test projects used in [SyncedProjectTest].
 *
 * When adding a new entry to this file add a new test method to [SyncedProjectTest].
 */
enum class TestProject(
  override val template: String,
  override val pathToOpen: String = "",
  override val testName: String? = null,
  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = { true },
  override val autoMigratePackageAttribute: Boolean = true,
  override val setup: () -> () -> Unit = { {} },
  override val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit = {},
  override val expectedSyncIssues: Set<Int> = emptySet(),
  override val verifyOpened: ((Project) -> Unit)? = null,
  override val switchVariant: TemplateBasedTestProject.VariantSelection? = null
) : TemplateBasedTestProject {
  APP_WITH_ML_MODELS(TestProjectToSnapshotPaths.APP_WITH_ML_MODELS),
  APP_WITH_BUILDSRC(TestProjectToSnapshotPaths.APP_WITH_BUILDSRC),
  APP_WITH_BUILDSRC_AND_SETTINGS_PLUGIN(
    TestProjectToSnapshotPaths.APP_WITH_BUILDSRC,
    testName = "buildSrcWithSettingsPlugin",
    patch = {
      it.resolve("settings.gradle").replaceContent { original ->
        original.replace("plugins {", """
          plugins {
            id("com.android.settings") version "${this.resolve().agpVersion}"
        """.trimIndent()
        )
      }
    },
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT }
  ),
  COMPATIBILITY_TESTS_AS_36(TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36, patch = { updateProjectJdk(it) }),
  COMPATIBILITY_TESTS_AS_36_NO_IML(TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36_NO_IML, patch = { updateProjectJdk(it) }),
  ANDROID_KOTLIN_MULTIPLATFORM(
    TestProjectToSnapshotPaths.ANDROID_KOTLIN_MULTIPLATFORM,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_82 },
  ),
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
  SIMPLE_APPLICATION_WITH_TRANSITIVE_DEPENDENCIES(TestProjectToSnapshotPaths.SIMPLE_APPLICATION_WITH_TRANSITIVE_DEPENDENCIES),
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
  SIMPLE_APPLICATION_WITH_ANDROID_CAR(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "withAndroidCar",
    patch = { root ->
      root.resolve("app/build.gradle").replaceContent {
        it + """
          android.useLibrary 'android.car'
         """
      }
    }
  ),
  SIMPLE_APPLICATION_SYNC_FAILED(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "syncFailed",
    verifyOpened = { project -> assertThat(GradleSyncState.Companion.getInstance(project).lastSyncFailed()).isTrue() },
    patch = { root ->
      root.resolve("build.gradle").writeText("*** this is an error ***")
    }
  ),
  CUSTOM_NAMESPACE(TestProjectToSnapshotPaths.CUSTOM_NAMESPACE),
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
  NON_STANDARD_SOURCE_SETS(
    TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_70 },
    pathToOpen = "/application"
  ),
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
  TEST_ONLY_MODULE(
    TestProjectToSnapshotPaths.TEST_ONLY_MODULE,
    patch = { projectRoot ->
      if (this < AgpVersionSoftwareEnvironmentDescriptor.AGP_42) {
        // Benchmarks sub-project is incompatible with <= 4.1.
        projectRoot.resolve("settings.gradle").replaceInContent(", ':benchmark'", "")
      }
    }
  ),
  KOTLIN_MULTIPLATFORM(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_70 }
  ),
  KOTLIN_MULTIPLATFORM_WITHJS(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "withjs",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, addJsModule = true) }
  ),
  KOTLIN_MULTIPLATFORM_IOS(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "ios",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, addIosTo = listOf("module2")) }
  ),
  KOTLIN_MULTIPLATFORM_JVM(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "jvm",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, addJvmTo = listOf("module2")) }
  ),
  KOTLIN_MULTIPLATFORM_JVM_KMPAPP(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "jvm_kmpapp",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { patchMppProject(it, convertAppToKmp = true, addJvmTo = listOf("app", "module2")) }
  ),
  KOTLIN_MULTIPLATFORM_JVM_KMPAPP_WITHINTERMEDIATE(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "jvm_kmpapp_withintermediate",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = {
      patchMppProject(
        it,
        convertAppToKmp = true,
        addJvmTo = listOf("app", "module2"),
        addIntermediateTo = listOf("module2")
      )
    }
  ),
  SIMPLE_APPLICATION_WITH_SCREENSHOT_TEST(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "simple_application_with_screenshot_test",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { projectRoot ->
      projectRoot.resolve("app").resolve("build.gradle").replaceContent { content ->
        content
          .replace(
            "buildTypes {",
            """// Do not remove. This is needed to test screenshot tests support.
                  experimentalProperties["android.experimental.enableScreenshotTest"] = true
                   buildTypes {
            """.trimMargin())
      }
      projectRoot.resolve("app").resolve("build.gradle").replaceContent { content ->
        content.replace(
          "dependencies {",
          "dependencies {\n  implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22'"
        )
      }
      projectRoot.resolve("gradle.properties").replaceContent { content ->
        content
          .replace("android.experimental.enableScreenshotTest=false", "android.experimental.enableScreenshotTest=true")
      }
      projectRoot.resolve("build.gradle").replaceContent { content ->
        content.replace(
          "classpath 'com.android.tools.build:gradle:",
          "classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22'\nclasspath 'com.android.tools.build:gradle:"
        )
      }
    }
  ),
  KOTLIN_MULTIPLATFORM_MULTIPLE_SOURCE_SET_PER_ANDROID_COMPILATION(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    testName = "multiple_source_set_per_android_compilation",
    isCompatibleWith = { it == AGP_CURRENT },
    patch = { projectRoot ->
      patchMppProject(projectRoot, convertAppToKmp = true)
      projectRoot.resolve("app").resolve("build.gradle").replaceInContent(
        "android()",
        """
          android()
            sourceSets {
              androidTest
              androidAndroidTest {
                dependsOn(androidTest)
              }
            }
        """.trimIndent()
      )
    }
  ),
  MULTI_FLAVOR(TestProjectToSnapshotPaths.MULTI_FLAVOR),
  MULTI_FLAVOR_SWITCH_VARIANT(
    TestProjectToSnapshotPaths.MULTI_FLAVOR,
    testName = "switchVariant",
    switchVariant = TemplateBasedTestProject.VariantSelection(":app", "firstXyzSecondXyzRelease")
  ),
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
  MAIN_IN_ROOT(
    TestProjectToSnapshotPaths.MAIN_IN_ROOT,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_80 }
    ),
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
  SIMPLE_APPLICATION_VERSION_CATALOG(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION_VERSION_CATALOG,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT }
  ),
  CUSTOM_SOURCE_TYPE(TestProjectToSnapshotPaths.CUSTOM_SOURCE_TYPE),
  LIGHT_SYNC_REFERENCE(TestProjectToSnapshotPaths.LIGHT_SYNC_REFERENCE),
  NON_TRANSITIVE_R_CLASS_SYMBOL(TestProjectToSnapshotPaths.NON_TRANSITIVE_R_CLASS_SYMBOL),
  NON_TRANSITIVE_R_CLASS_SYMBOL_TRUE(
    TestProjectToSnapshotPaths.NON_TRANSITIVE_R_CLASS_SYMBOL,
    testName = "_non_transitive_r_class_symbol_true",
    patch = { projectRoot ->
      projectRoot.resolve("gradle.properties").replaceContent { content ->
        content.replace("android.nonTransitiveRClass=false", "android.nonTransitiveRClass=true")
      }
    }
  ),
  MIGRATE_TO_NON_TRANSITIVE_R_CLASSES(TestProjectToSnapshotPaths.MIGRATE_TO_NON_TRANSITIVE_R_CLASSES),
  PURE_JAVA_PROJECT(TestProjectToSnapshotPaths.PURE_JAVA_PROJECT),
  BUILDSRC_WITH_COMPOSITE(TestProjectToSnapshotPaths.BUILDSRC_WITH_COMPOSITE),
  PRIVACY_SANDBOX_SDK(
    TestProjectToSnapshotPaths.PRIVACY_SANDBOX_SDK,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT },
  ),
  APP_WITH_BUILD_FEATURES_ENABLED(TestProjectToSnapshotPaths.APP_WITH_BUILD_FEATURES_ENABLED),
  DEPENDENT_MODULES_ONLY_APP_RUNTIME(
    TestProjectToSnapshotPaths.DEPENDENT_MODULES,
    testName = "noLibraryRuntime",
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT },
    patch = { projectRoot: File ->
      projectRoot.resolve("gradle.properties").appendText("\nandroid.dependency.excludeLibraryComponentsFromConstraints=true")
    }
    ),
  INDEPENDENT_MODULES_ONLY_RUNTIME(
    TestProjectToSnapshotPaths.DEPENDENT_MODULES,
    testName = "noLibraryRuntimeIndependentModules",
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT },
    patch = { projectRoot: File ->
      projectRoot.resolve("gradle.properties").appendText("\nandroid.dependency.excludeLibraryComponentsFromConstraints=true")
      projectRoot.resolve("app").resolve("build.gradle").replaceContent {
        it.replace("api project(\":lib\")", "")
      }
    }
  ),
  BUILD_CONFIG_AS_BYTECODE_ENABLED(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    testName = "buildConfigAsBytecodeEnabled",
    patch = { projectRoot ->
      projectRoot.resolve("gradle.properties").appendText("\nandroid.enableBuildConfigAsBytecode=true")
      projectRoot.resolve("app/build.gradle").appendText(
        """
          
          android.buildFeatures.buildConfig true
        """.trimIndent()
      )
    }
  ),
  GRADLE_DECLARATIVE(
    TestProjectToSnapshotPaths.GRADLE_DECLARATIVE,
    setup = fun(): () -> Unit {
      StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true)

      return fun() {
        StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride()
      }
    },
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT },
  );

  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"

  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))
}

/**
 * Other test projects not included in `SyncedProjectTest`.
 */
enum class TestProjectOther(
  override val template: String,
  override val pathToOpen: String = "",
  override val testName: String? = null,
  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = { true },
  override val autoMigratePackageAttribute: Boolean = true,
  override val setup: () -> () -> Unit = { {} },
  override val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit = {},
  override val expectedSyncIssues: Set<Int> = emptySet(),
  override val verifyOpened: ((Project) -> Unit)? = null,
  override val switchVariant: TemplateBasedTestProject.VariantSelection? = null
) : TemplateBasedTestProject {
  JPS_WITH_QUALIFIED_NAMES(TestProjectToSnapshotPaths.JPS_WITH_QUALIFIED_NAMES),
  SIMPLE_APPLICATION_CORRUPTED_MISSING_IML_40(TestProjectToSnapshotPaths.SIMPLE_APPLICATION_CORRUPTED_MISSING_IML_40),
  ;

  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"

  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))
}

open class TestProjectTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  private val namespaceSubstring = """namespace = "google.simpleapplication"""" // Do not inline as it needs to be the same in both tests.
  private val packageSubstring = """package="google.simpleapplication"""" // Do not inline.

  open fun testMigratePackageAttribute_agp71() {
    val preparedProject71 =
      projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_71)

    val root = preparedProject71.root
    expect.that(root.resolve("app/build.gradle").readText()).doesNotContain(namespaceSubstring)
    expect.that(root.resolve("app/src/main/AndroidManifest.xml").readText()).contains(packageSubstring)
  }

  open fun testMigratePackageAttribute_agp80() {
    val preparedProject80 =
      projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_80)
    val root = preparedProject80.root
    expect.that(root.resolve("app/build.gradle").readText()).contains(namespaceSubstring)
    expect.that(root.resolve("app/src/main/AndroidManifest.xml").readText()).doesNotContain(packageSubstring)
  }
}