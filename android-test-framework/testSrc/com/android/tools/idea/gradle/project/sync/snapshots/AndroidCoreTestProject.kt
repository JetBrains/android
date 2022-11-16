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

import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_74
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import java.io.File

/**
 * Defines test projects used in [SyncedProjectTest].
 *
 * When adding a new entry to this file add a new test method to [SyncedProjectTest].
 */
enum class AndroidCoreTestProject(
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
  ANDROID_LIBRARY_AS_TEST_DEPENDENCY(TestProjectPaths.ANDROID_LIBRARY_AS_TEST_DEPENDENCY),
  APP_WITH_BUILDSRC(TestProjectPaths.APP_WITH_BUILDSRC),
  APP_WITH_ACTIVITY_IN_LIB(TestProjectPaths.APP_WITH_ACTIVITY_IN_LIB, isCompatibleWith = { it >= AGP_74 }),
  APPLICATION_ID_SUFFIX(TestProjectPaths.APPLICATION_ID_SUFFIX),
  APPLICATION_ID_VARIANT_API(TestProjectPaths.APPLICATION_ID_VARIANT_API),
  APPLICATION_ID_VARIANT_API_BROKEN(TestProjectPaths.APPLICATION_ID_VARIANT_API_BROKEN),
  BASIC(TestProjectPaths.BASIC),
  BUDDY_APKS(TestProjectPaths.BUDDY_APKS),
  BUILD_ANALYZER_CHECK_ANALYZERS(TestProjectPaths.BUILD_ANALYZER_CHECK_ANALYZERS),
  COMPOSITE_BUILD(TestProjectPaths.COMPOSITE_BUILD),
  DEPENDENT_MODULES(TestProjectPaths.DEPENDENT_MODULES),
  DEPENDENT_NATIVE_MODULES(TestProjectPaths.DEPENDENT_NATIVE_MODULES),
  DYNAMIC_APP(TestProjectPaths.DYNAMIC_APP),
  DYNAMIC_APP_WITH_VARIANTS(TestProjectPaths.DYNAMIC_APP_WITH_VARIANTS),
  HELLO_JNI(TestProjectPaths.HELLO_JNI),
  INSTANT_APP(TestProjectPaths.INSTANT_APP),
  INSTANT_APP_WITH_DYNAMIC_FEATURES(TestProjectPaths.INSTANT_APP_WITH_DYNAMIC_FEATURES),
  KOTLIN_KAPT(TestProjectPaths.KOTLIN_KAPT),
  NAVIGATION_EDITOR_INCLUDE_FROM_LIB(TestProjectPaths.NAVIGATION_EDITOR_INCLUDE_FROM_LIB),
  WITH_ERRORS_SIMPLE_APPLICATION_MISSING_EXPORT(TestProjectPaths.WITH_ERRORS_SIMPLE_APPLICATION_MISSING_EXPORT),
  WITH_ERRORS_SIMPLE_APPLICATION_MULTIPLE_ERRORS(TestProjectPaths.WITH_ERRORS_SIMPLE_APPLICATION_MULTIPLE_ERRORS),
  NESTED_MODULE(TestProjectPaths.NESTED_MODULE),
  PRIVACY_SANDBOX_SDK_LIBRARY_AND_CONSUMER(TestProjectPaths.PRIVACY_SANDBOX_SDK_LIBRARY_AND_CONSUMER),
  PROJECT_WITH_APPAND_LIB(TestProjectPaths.PROJECT_WITH_APPAND_LIB),
  PSD_DEPENDENCY(TestProjectPaths.PSD_DEPENDENCY),
  PSD_PROJECT_DIR(TestProjectPaths.PSD_PROJECT_DIR),
  PSD_SAMPLE_GROOVY(TestProjectPaths.PSD_SAMPLE_GROOVY),
  PSD_SAMPLE_KOTLIN(TestProjectPaths.PSD_SAMPLE_KOTLIN),
  PSD_UPGRADE(TestProjectPaths.PSD_UPGRADE),
  PSD_VERSION_CATALOG_SAMPLE_GROOVY(TestProjectPaths.PSD_VERSION_CATALOG_SAMPLE_GROOVY),
  PSD_VARIANT_COLLISIONS(TestProjectPaths.PSD_VARIANT_COLLISIONS),
  RUN_APP_36(TestProjectPaths.RUN_APP_36),
  PROJECT_WITH_APP_AND_LIB_DEPENDENCY(TestProjectPaths.PROJECT_WITH_APP_AND_LIB_DEPENDENCY),
  RUN_CONFIG_ACTIVITY(TestProjectPaths.RUN_CONFIG_ACTIVITY),
  RUN_CONFIG_WATCHFACE(TestProjectPaths.RUN_CONFIG_WATCHFACE),
  SCRIPTED_DIMENSIONS(TestProjectPaths.SCRIPTED_DIMENSIONS),
  SIMPLE_APP_WITH_OLDER_SUPPORT_LIB(TestProjectPaths.SIMPLE_APP_WITH_OLDER_SUPPORT_LIB),
  SIMPLE_APPLICATION(TestProjectPaths.SIMPLE_APPLICATION),
  SIMPLE_APPLICATION_PLUGINS_DSL(TestProjectPaths.SIMPLE_APPLICATION_PLUGINS_DSL),
  SPLIT_BUILD_FILES(TestProjectPaths.SPLIT_BUILD_FILES),
  TEST_FIXTURES(TestProjectPaths.TEST_FIXTURES),
  TRANSITIVE_DEPENDENCIES(TestProjectPaths.TRANSITIVE_DEPENDENCIES),
  UNIT_TESTING(TestProjectPaths.UNIT_TESTING),
  UNUSED_RESOURCES_GROOVY(TestProjectPaths.UNUSED_RESOURCES_GROOVY),
  UNUSED_RESOURCES_KTS(TestProjectPaths.UNUSED_RESOURCES_KTS),
  UNUSED_RESOURCES_MULTI_MODULE(TestProjectPaths.UNUSED_RESOURCES_MULTI_MODULE),
  WEAR_WATCHFACE(
    TestProjectPaths.WEAR_WATCHFACE,
    isCompatibleWith = { it >= AGP_40 }
  ),
  ;

  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData"

  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)))
}