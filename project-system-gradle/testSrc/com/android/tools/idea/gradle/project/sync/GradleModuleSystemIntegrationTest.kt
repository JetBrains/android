/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.manifmerger.ManifestSystemProperty
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTest
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.DependencyScopeType.ANDROID_TEST
import com.android.tools.idea.projectsystem.DependencyScopeType.MAIN
import com.android.tools.idea.projectsystem.DependencyScopeType.UNIT_TEST
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import java.io.File

data class GradleModuleSystemIntegrationTest(
  override val name: String,
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
  val body: (project: Project, expect: Expect) -> Unit
) : SyncedProjectTest.TestDef {

  companion object {
    val tests =
      listOf(
        GradleModuleSystemIntegrationTest(
          name = "manifestOverrides",
          testProject = TestProject.MULTI_FLAVOR
        ) { project, expect ->
          val overrides = project.gradleModule(":app")!!.getModuleSystem().getManifestOverrides().directOverrides
          expect.that(overrides[ManifestSystemProperty.Instrumentation.FUNCTIONAL_TEST]).isNull()
          expect.that(overrides[ManifestSystemProperty.Instrumentation.HANDLE_PROFILING]).isNull()
          expect.that(overrides[ManifestSystemProperty.Instrumentation.LABEL]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION]).isEqualTo("16")
          expect.that(overrides[ManifestSystemProperty.Instrumentation.NAME]).isNull()
          expect.that(overrides[ManifestSystemProperty.Document.PACKAGE]).isEqualTo("com.example.multiflavor.firstAbc.secondAbc.debug")
          expect.that(overrides[ManifestSystemProperty.Instrumentation.TARGET_PACKAGE]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION])
            .isEqualTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString())
          expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_CODE]).isEqualTo("20")
          expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_NAME]).isEqualTo("1.secondAbc-firstAbc-secondAbc-debug")
          expect.that(overrides[ManifestSystemProperty.Profileable.SHELL]).isNull()
          expect.that(overrides[ManifestSystemProperty.Profileable.ENABLED]).isNull()
          expect.that(overrides[ManifestSystemProperty.Application.TEST_ONLY]).isNull()
          expect.that(ManifestSystemProperty.values.size).isEqualTo(14)
        },
        GradleModuleSystemIntegrationTest(
          name = "manifestOverrides_firstXyzSecondXyzRelease",
          testProject = TestProject.MULTI_FLAVOR_SWITCH_VARIANT
        ) { project, expect ->
          val overrides = project.gradleModule(":app")!!.getModuleSystem().getManifestOverrides().directOverrides
          expect.that(overrides[ManifestSystemProperty.Instrumentation.FUNCTIONAL_TEST]).isNull()
          expect.that(overrides[ManifestSystemProperty.Instrumentation.HANDLE_PROFILING]).isNull()
          expect.that(overrides[ManifestSystemProperty.Instrumentation.LABEL]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION]).isEqualTo("16")
          expect.that(overrides[ManifestSystemProperty.Instrumentation.NAME]).isNull()
          expect.that(overrides[ManifestSystemProperty.Document.PACKAGE]).isEqualTo("com.example.multiflavor.secondXyz.release")
          expect.that(overrides[ManifestSystemProperty.Instrumentation.TARGET_PACKAGE]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION])
            .isEqualTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString())
          expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_CODE]).isEqualTo("31")
          expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_NAME]).isEqualTo("1.0-secondXyz-release")
          expect.that(overrides[ManifestSystemProperty.Profileable.SHELL]).isNull()
          expect.that(overrides[ManifestSystemProperty.Profileable.ENABLED]).isNull()
          expect.that(overrides[ManifestSystemProperty.Application.TEST_ONLY]).isNull()
          expect.that(ManifestSystemProperty.values.size).isEqualTo(14)
        },
        GradleModuleSystemIntegrationTest(
          name = "manifestOverridesInLibrary",
          testProject = TestProject.INCLUDE_FROM_LIB
        ) { project, expect ->
          val overrides = project.gradleModule(":lib")!!.getModuleSystem().getManifestOverrides().directOverrides
          assertThat(overrides).containsExactlyEntriesIn(
            mapOf(
              ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION to "16",
              ManifestSystemProperty.Document.PACKAGE to "com.example.lib",
              ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION to SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
              ManifestSystemProperty.Manifest.VERSION_CODE to "1",
              ManifestSystemProperty.Manifest.VERSION_NAME to "1.0",
            )
          )
        },
        GradleModuleSystemIntegrationTest(
          name = "manifestOverridesInSeparateTest",
          testProject = TestProject.TEST_ONLY_MODULE
        ) { project, expect ->
          val overrides = project.gradleModule(":test")!!.getModuleSystem().getManifestOverrides().directOverrides
          assertThat(overrides).containsExactlyEntriesIn(
            mapOf(
              ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION to "16",
              ManifestSystemProperty.Document.PACKAGE to "com.example.android.app.testmodule",
              ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION to SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
            )
          )
        },
        GradleModuleSystemIntegrationTest(
          name = "packageName",
          testProject = TestProject.MULTI_FLAVOR
        ) { project, expect ->
          val packageName = project.gradleModule(":app")!!.getModuleSystem().getPackageName()
          expect.that(packageName).isEqualTo("com.example.multiflavor")
        },
        GradleModuleSystemIntegrationTest(
          name = "packageName_firstXyzSecondXyzRelease",
          testProject = TestProject.MULTI_FLAVOR_SWITCH_VARIANT
        ) { project, expect ->
          val packageName = project.gradleModule(":app")!!.getModuleSystem().getPackageName()
          expect.that(packageName).isEqualTo("com.example.multiflavor")
        },
        GradleModuleSystemIntegrationTest(
          name = "allApplicationIds",
          testProject = TestProject.MULTI_FLAVOR
        ) { project, expect ->
          val appIds = AndroidModel.get(project.gradleModule(":app")!!)?.allApplicationIds.orEmpty()
          expect.that(appIds).isEqualTo(
            setOf(
              "com.example.multiflavor.firstAbc.secondAbc.debug",
              "com.example.multiflavor.firstAbc.secondXyz.debug",
              "com.example.multiflavor.secondAbc.debug",
              "com.example.multiflavor.secondXyz.debug",
              "com.example.multiflavor.firstAbc.secondAbc.release",
              "com.example.multiflavor.firstAbc.secondXyz.release",
              "com.example.multiflavor.secondAbc.release",
              "com.example.multiflavor.secondXyz.release"
            )
          )
        },
        GradleModuleSystemIntegrationTest(
          name = "getResolvedDependency",
          testProject = TestProject.SIMPLE_APPLICATION
        ) { project, expect ->
          val module = project.gradleModule(":app")?.getModuleSystem() ?: error(":app module not found")
          expect
            .that(module.getResolvedDependency("com.google.guava:guava:+".gradleCoordinate, MAIN)?.version)
            .isEqualTo("19.0".gradleVersion)
          expect
            .that(module.getResolvedDependency("junit:junit:+".gradleCoordinate, MAIN)?.version)
            .isNull()
          expect
            .that(module.getResolvedDependency("com.android.support.test.espresso:espresso-core:+".gradleCoordinate, MAIN)?.version)
            .isNull()

          expect
            .that(module.getResolvedDependency("com.google.guava:guava:+".gradleCoordinate, UNIT_TEST)?.version)
            .isEqualTo("19.0".gradleVersion)
          expect
            .that(module.getResolvedDependency("junit:junit:+".gradleCoordinate, UNIT_TEST)?.version)
            .isEqualTo("4.12".gradleVersion)
          expect
            .that(module.getResolvedDependency("com.android.support.test.espresso:espresso-core:+".gradleCoordinate, UNIT_TEST)?.version)
            .isNull()

          expect
            .that(module.getResolvedDependency("com.google.guava:guava:+".gradleCoordinate, ANDROID_TEST)?.version)
            .isEqualTo("19.0".gradleVersion)
          expect
            .that(module.getResolvedDependency("junit:junit:+".gradleCoordinate, ANDROID_TEST)?.version)
            .isEqualTo("4.12".gradleVersion)
          expect
            .that(module.getResolvedDependency("com.android.support.test.espresso:espresso-core:+".gradleCoordinate, ANDROID_TEST)?.version)
            .isEqualTo("3.0.2".gradleVersion)
        },
      )
  }

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTest.TestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun runTest(root: File, project: Project, expect: Expect) {
    body(project, expect)
  }

  override fun runTest(root: File, project: Project) = error("Another variant is overriden")
}

private val String.gradleCoordinate
  get() =
    GradleCoordinate.parseCoordinateString(this) ?: error("Invalid gradle coordinate: $this")

private val String.gradleVersion get() = GradleVersion.parse(this)

