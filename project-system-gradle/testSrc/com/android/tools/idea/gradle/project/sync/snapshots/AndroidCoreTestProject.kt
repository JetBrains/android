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
  APPLICATION_ID_SUFFIX(TestProjectPaths.APPLICATION_ID_SUFFIX),
  APPLICATION_ID_VARIANT_API(TestProjectPaths.APPLICATION_ID_VARIANT_API),
  APPLICATION_ID_VARIANT_API_BROKEN(TestProjectPaths.APPLICATION_ID_VARIANT_API_BROKEN),
  BUDDY_APKS(TestProjectPaths.BUDDY_APKS),
  COMPOSITE_BUILD(TestProjectPaths.COMPOSITE_BUILD),
  DEPENDENT_MODULES(TestProjectPaths.DEPENDENT_MODULES),
  DEPENDENT_NATIVE_MODULES(TestProjectPaths.DEPENDENT_NATIVE_MODULES),
  DYNAMIC_APP(TestProjectPaths.DYNAMIC_APP),
  DYNAMIC_APP_WITH_VARIANTS(TestProjectPaths.DYNAMIC_APP_WITH_VARIANTS),
  HELLO_JNI(TestProjectPaths.HELLO_JNI),
  INSTANT_APP(TestProjectPaths.INSTANT_APP),
  KOTLIN_KAPT(TestProjectPaths.KOTLIN_KAPT),
  PRIVACY_SANDBOX_SDK_LIBRARY_AND_CONSUMER(TestProjectPaths.PRIVACY_SANDBOX_SDK_LIBRARY_AND_CONSUMER),
  PSD_DEPENDENCY(TestProjectPaths.PSD_DEPENDENCY),
  RUN_APP_36(TestProjectPaths.RUN_APP_36),
  PROJECT_WITH_APP_AND_LIB_DEPENDENCY(TestProjectPaths.PROJECT_WITH_APP_AND_LIB_DEPENDENCY),
  RUN_CONFIG_ACTIVITY(TestProjectPaths.RUN_CONFIG_ACTIVITY),
  RUN_CONFIG_WATCHFACE(TestProjectPaths.RUN_CONFIG_WATCHFACE),
  TRANSITIVE_DEPENDENCIES(TestProjectPaths.TRANSITIVE_DEPENDENCIES),
  UNIT_TESTING(TestProjectPaths.UNIT_TESTING),
  ;

  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData"

  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)))
}