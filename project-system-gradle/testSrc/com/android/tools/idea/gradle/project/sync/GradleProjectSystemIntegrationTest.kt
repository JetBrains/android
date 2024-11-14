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

import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GradleCoordinate
import com.android.manifmerger.ManifestSystemProperty
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.project.FacetBasedApplicationProjectContext
import com.android.tools.idea.projectsystem.DependencyScopeType.ANDROID_TEST
import com.android.tools.idea.projectsystem.DependencyScopeType.MAIN
import com.android.tools.idea.projectsystem.DependencyScopeType.UNIT_TEST
import com.android.tools.idea.projectsystem.PseudoLocalesToken
import com.android.tools.idea.projectsystem.PseudoLocalesToken.Companion.isPseudoLocalesEnabled
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import java.io.File

data class GradleProjectSystemIntegrationTest(
  override val name: String,
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
  val body: (project: Project, agpVersion: AgpVersionSoftwareEnvironmentDescriptor, expect: Expect) -> Unit
) : SyncedProjectTestDef {

  companion object {
    val tests =
      listOf(
        GradleProjectSystemIntegrationTest(
          name = "manifestOverrides",
          testProject = TestProject.MULTI_FLAVOR
        ) { project, agpVersion, expect ->
          val overrides = project.gradleModule(":app")!!.getModuleSystem().getManifestOverrides().directOverrides
          expect.that(overrides[ManifestSystemProperty.Instrumentation.FUNCTIONAL_TEST]).isNull()
          expect.that(overrides[ManifestSystemProperty.Instrumentation.HANDLE_PROFILING]).isNull()
          expect.that(overrides[ManifestSystemProperty.Instrumentation.LABEL]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION]).isEqualTo("16")
          expect.that(overrides[ManifestSystemProperty.Instrumentation.NAME]).isNull()
          expect.that(overrides[ManifestSystemProperty.Document.PACKAGE]).isEqualTo("com.example.multiflavor.firstAbc.secondAbc.debug")
          expect.that(overrides[ManifestSystemProperty.Instrumentation.TARGET_PACKAGE]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION]).isEqualTo(agpVersion.targetSdk)
          expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_CODE]).isEqualTo("20")
          expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_NAME]).isEqualTo("1.secondAbc-firstAbc-secondAbc-debug")
          expect.that(overrides[ManifestSystemProperty.Profileable.SHELL]).isNull()
          expect.that(overrides[ManifestSystemProperty.Profileable.ENABLED]).isNull()
          expect.that(overrides[ManifestSystemProperty.Application.TEST_ONLY]).isNull()
          expect.that(ManifestSystemProperty.values.size).isEqualTo(15)
        },
        GradleProjectSystemIntegrationTest(
          name = "manifestOverrides_firstXyzSecondXyzRelease",
          testProject = TestProject.MULTI_FLAVOR_SWITCH_VARIANT
        ) { project, agpVersion, expect ->
          val overrides = project.gradleModule(":app")!!.getModuleSystem().getManifestOverrides().directOverrides
          expect.that(overrides[ManifestSystemProperty.Instrumentation.FUNCTIONAL_TEST]).isNull()
          expect.that(overrides[ManifestSystemProperty.Instrumentation.HANDLE_PROFILING]).isNull()
          expect.that(overrides[ManifestSystemProperty.Instrumentation.LABEL]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION]).isEqualTo("16")
          expect.that(overrides[ManifestSystemProperty.Instrumentation.NAME]).isNull()
          expect.that(overrides[ManifestSystemProperty.Document.PACKAGE]).isEqualTo("com.example.multiflavor.secondXyz.release")
          expect.that(overrides[ManifestSystemProperty.Instrumentation.TARGET_PACKAGE]).isNull()
          expect.that(overrides[ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION]).isEqualTo(agpVersion.targetSdk)
          expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_CODE]).isEqualTo("31")
          expect.that(overrides[ManifestSystemProperty.Manifest.VERSION_NAME]).isEqualTo("1.0-secondXyz-release")
          expect.that(overrides[ManifestSystemProperty.Profileable.SHELL]).isNull()
          expect.that(overrides[ManifestSystemProperty.Profileable.ENABLED]).isNull()
          expect.that(overrides[ManifestSystemProperty.Application.TEST_ONLY]).isNull()
          expect.that(ManifestSystemProperty.values.size).isEqualTo(15)
        },
        GradleProjectSystemIntegrationTest(
          name = "manifestOverridesInLibrary",
          testProject = TestProject.INCLUDE_FROM_LIB
        ) { project, agpVersion, expect ->
          val overrides = project.gradleModule(":lib")!!.getModuleSystem().getManifestOverrides().directOverrides
          assertThat(overrides).containsExactlyEntriesIn(
            mapOf(
              ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION to "16",
              ManifestSystemProperty.Document.PACKAGE to "com.example.lib",
              ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION to agpVersion.targetSdk,
              ManifestSystemProperty.Manifest.VERSION_CODE to "1",
              ManifestSystemProperty.Manifest.VERSION_NAME to "1.0",
            )
          )
        },
        GradleProjectSystemIntegrationTest(
          name = "manifestOverridesInSeparateTest",
          testProject = TestProject.TEST_ONLY_MODULE
        ) { project, agpVersion, expect ->
          val overrides = project.gradleModule(":test")!!.getModuleSystem().getManifestOverrides().directOverrides
          assertThat(overrides).containsExactlyEntriesIn(
            mapOf(
              ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION to "16",
              ManifestSystemProperty.Document.PACKAGE to "com.example.android.app.testmodule",
              ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION to agpVersion.targetSdk,
            )
          )
        },
        GradleProjectSystemIntegrationTest(
          name = "packageName",
          testProject = TestProject.MULTI_FLAVOR
        ) { project, agpVersion, expect ->
          val packageName = project.gradleModule(":app")!!.getModuleSystem().getPackageName()
          expect.that(packageName).isEqualTo("com.example.multiflavor")
        },
        GradleProjectSystemIntegrationTest(
          name = "packageName_firstXyzSecondXyzRelease",
          testProject = TestProject.MULTI_FLAVOR_SWITCH_VARIANT
        ) { project, agpVersion, expect ->
          val packageName = project.gradleModule(":app")!!.getModuleSystem().getPackageName()
          expect.that(packageName).isEqualTo("com.example.multiflavor")
        },
        GradleProjectSystemIntegrationTest(
          name = "isValidAndroidManifestPackage",
          testProject = TestProject.TRANSITIVE_DEPENDENCIES
        ) { project, agpVersion, expect ->
          val projectSystem = project.getProjectSystem()
          // All namespaces and superpackages of namespaces should resolve
          listOf(
            "com",
            "com.example",
            "com.example.alruiz",
            "com.example.alruiz.transitive_dependencies",
            "com.example.alruiz.transitive_dependencies.test",
            "com.example.library1",
            "com.example.library1.test",
            "com.example.library2",
            "com.example.library2.test",
          ).forEach { packageName ->
            expect.that(projectSystem.isNamespaceOrParentPackage(packageName))
              .named("projectSystem.isValidAndroidManifestPackage(\"$packageName\")")
              .isTrue()
          }
          listOf(
            // R classes have to have a package
            "",
            // Substrings of valid packages should not resolve
            "co",
            "com.exampl",
            // Nor should subpackages
            "com.example.library1.other",
            "com.example.library3",
          ).forEach { packageName ->
            expect.that(projectSystem.isNamespaceOrParentPackage(packageName))
              .named("projectSystem.isValidAndroidManifestPackage(\"$packageName\")")
              .isFalse()
          }
        },
        GradleProjectSystemIntegrationTest(
          name = "module_allApplicationIds",
          testProject = TestProject.MULTI_FLAVOR
        ) { project, agpVersion,  expect ->
          val appIds = AndroidModel.get(project.gradleModule(":app")!!)?.allApplicationIds.orEmpty()
          expect.that(appIds).containsExactly(
            "com.example.multiflavor.firstAbc.secondAbc.debug",
            "com.example.multiflavor.firstAbc.secondAbc.debug.test",
            "com.example.multiflavor.firstAbc.secondXyz.debug",
            "com.example.multiflavor.firstAbc.secondXyz.debug.test",
            "com.example.multiflavor.secondAbc.debug",
            "com.example.multiflavor.secondAbc.debug.test",
            "com.example.multiflavor.secondXyz.debug",
            "com.example.multiflavor.secondXyz.debug.test",
            "com.example.multiflavor.firstAbc.secondAbc.release",
            "com.example.multiflavor.firstAbc.secondXyz.release",
            "com.example.multiflavor.secondAbc.release",
            "com.example.multiflavor.secondXyz.release"
          )
        },
        GradleProjectSystemIntegrationTest(
          name = "knownApplicationIds",
          testProject = TestProject.MULTI_FLAVOR
        ) { project, agpVersion, expect ->
          val appIds = project.getProjectSystem().getKnownApplicationIds(project)
          expect.that(appIds).containsExactly(
            "com.example.multiflavor.firstAbc.secondAbc.debug",
            "com.example.multiflavor.firstAbc.secondAbc.debug.test",
            "com.example.multiflavor.firstAbc.secondAbc.release",
            "com.example.multiflavor.firstAbc.secondXyz.debug",
            "com.example.multiflavor.firstAbc.secondXyz.debug.test",
            "com.example.multiflavor.firstAbc.secondXyz.release",
            "com.example.multiflavor.secondAbc.debug",
            "com.example.multiflavor.secondAbc.debug.test",
            "com.example.multiflavor.secondAbc.release",
            "com.example.multiflavor.secondXyz.debug",
            "com.example.multiflavor.secondXyz.debug.test",
            "com.example.multiflavor.secondXyz.release",
          )
        },
        GradleProjectSystemIntegrationTest(
          name = "knownApplicationIds",
          testProject = TestProject.TRANSITIVE_DEPENDENCIES
        ) { project, agpVersion, expect ->
          val appIds = project.getProjectSystem().getKnownApplicationIds(project)
          expect.that(appIds).containsExactly(
            "com.example.alruiz.transitive_dependencies",
            "com.example.alruiz.transitive_dependencies.test",
            "com.example.library1.test",
            "com.example.library2.test",
          )
        },
        GradleProjectSystemIntegrationTest(
          name = "knownApplicationIds",
          testProject = TestProject.PSD_SAMPLE_GROOVY
        ) { project, agpVersion,  expect ->
          val appIds = project.getProjectSystem().getKnownApplicationIds(project)
          expect.that(appIds).containsExactly(
            "com.example.dyn_feature.test",
            "com.example.nested1.deep.test",
            "com.example.nested1.test",
            "com.example.nested2.deep.test",
            "com.example.nested2.test",
            "com.example.nested2.trans.deep2.test",
            "com.example.projectwithappandlib.lib.test",
            "com.example.psd.sample.app.default.test",
            "com.example.psd.sample.app.defaultSuffix",
            "com.example.psd.sample.app.defaultSuffix.barSuffix",
            "com.example.psd.sample.app.defaultSuffix.barSuffix.suffix",
            "com.example.psd.sample.app.defaultSuffix.suffix",
            "com.example.psd.sample.app.paid.test",
            "com.example.psd.sample.app.paid.defaultSuffix",
            "com.example.psd.sample.app.paid.defaultSuffix.barSuffix",
            "com.example.psd.sample.app.paid.defaultSuffix.barSuffix.suffix",
            "com.example.psd.sample.app.paid.defaultSuffix.suffix",
          )
        },
        GradleProjectSystemIntegrationTest(
          name = "findModulesWithApplicationId",
          testProject = TestProject.PSD_SAMPLE_GROOVY
        ) { project, agpVersion, expect ->
          fun doLookup(applicationId: String) =
            project.getProjectSystem().findModulesWithApplicationId(applicationId).map { it.name }
          expect.that(doLookup("com.example.dyn_feature.test")).containsExactly("project.dyn_feature.androidTest")
          expect.that(doLookup("com.example.nested1.deep.test")).containsExactly("project.nested1.deep.androidTest")
          expect.that(doLookup("com.example.nested1.test")).containsExactly("project.nested1.androidTest")
          expect.that(doLookup("com.example.nested2.deep.test")).containsExactly("project.nested2.deep.androidTest")
          expect.that(doLookup("com.example.nested2.test")).containsExactly("project.nested2.androidTest")
          expect.that(doLookup("com.example.nested2.trans.deep2.test")).containsExactly("project.nested2.trans.deep2.androidTest")
          expect.that(doLookup("com.example.projectwithappandlib.lib.test")).containsExactly("project.lib.androidTest")
          expect.that(doLookup("com.example.psd.sample.app.default.test")).containsExactly("project.app.androidTest")
          expect.that(doLookup("com.example.psd.sample.app.defaultSuffix")).containsExactly("project.app.main")
          expect.that(doLookup("com.example.psd.sample.app.defaultSuffix.barSuffix")).containsExactly("project.app.main")
          expect.that(doLookup("com.example.psd.sample.app.defaultSuffix.barSuffix.suffix")).containsExactly("project.app.main")
          expect.that(doLookup("com.example.psd.sample.app.defaultSuffix.suffix")).containsExactly("project.app.main")
          expect.that(doLookup("com.example.psd.sample.app.paid.test")).containsExactly("project.app.androidTest")
          expect.that(doLookup("com.example.psd.sample.app.paid.defaultSuffix")).containsExactly("project.app.main")
          expect.that(doLookup("com.example.psd.sample.app.paid.defaultSuffix.barSuffix")).containsExactly("project.app.main")
          expect.that(doLookup("com.example.psd.sample.app.paid.defaultSuffix.barSuffix.suffix")).containsExactly("project.app.main")
          expect.that(doLookup("com.example.psd.sample.app.paid.defaultSuffix.suffix")).containsExactly("project.app.main")
        },
        GradleProjectSystemIntegrationTest(
          name = "pseudoLocalesToken",
          testProject = TestProject.PSD_SAMPLE_GROOVY
        ) { project, agpVersion, expect ->
          fun doLookup(applicationId: String) =
            project.getProjectSystem().isPseudoLocalesEnabled(
              applicationProjectContext = FacetBasedApplicationProjectContext(
                applicationId = applicationId,
                facet = project.getProjectSystem().findModulesWithApplicationId(applicationId).single().androidFacet!!))
          expect.that(doLookup("com.example.dyn_feature.test")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.nested1.deep.test")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.nested1.test")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.nested2.deep.test")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.nested2.test")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.nested2.trans.deep2.test")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.projectwithappandlib.lib.test")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.psd.sample.app.default.test")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.ENABLED) // tested build type is debug
          expect.that(doLookup("com.example.psd.sample.app.defaultSuffix")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.BOTH) // used for both debug and specialRelease
          expect.that(doLookup("com.example.psd.sample.app.defaultSuffix.barSuffix")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.BOTH) // used for both debug and specialRelease
          expect.that(doLookup("com.example.psd.sample.app.defaultSuffix.barSuffix.suffix")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.psd.sample.app.defaultSuffix.suffix")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.psd.sample.app.paid.test")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.ENABLED) // tested build type is debug
          expect.that(doLookup("com.example.psd.sample.app.paid.defaultSuffix")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.BOTH) // used for both debug and specialRelease
          expect.that(doLookup("com.example.psd.sample.app.paid.defaultSuffix.barSuffix")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.BOTH) // used for both debug and specialRelease
          expect.that(doLookup("com.example.psd.sample.app.paid.defaultSuffix.barSuffix.suffix")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
          expect.that(doLookup("com.example.psd.sample.app.paid.defaultSuffix.suffix")).isEqualTo(PseudoLocalesToken.PseudoLocalesState.DISABLED)
        },
        GradleProjectSystemIntegrationTest(
          name = "getResolvedDependency",
          testProject = TestProject.SIMPLE_APPLICATION
        ) { project, agpVersion, expect ->
          val module = project.gradleModule(":app")?.getModuleSystem() ?: error(":app module not found")
          expect
            .that(module.getResolvedDependency("com.google.guava:guava:+".gradleCoordinate, MAIN)?.lowerBoundVersion)
            .isEqualTo("19.0".version)
          expect
            .that(module.getResolvedDependency("junit:junit:+".gradleCoordinate, MAIN)?.lowerBoundVersion)
            .isNull()
          expect
            .that(module.getResolvedDependency("com.android.support.test.espresso:espresso-core:+".gradleCoordinate, MAIN)?.lowerBoundVersion)
            .isNull()

          expect
            .that(module.getResolvedDependency("com.google.guava:guava:+".gradleCoordinate, UNIT_TEST)?.lowerBoundVersion)
            .isEqualTo("19.0".version)
          expect
            .that(module.getResolvedDependency("junit:junit:+".gradleCoordinate, UNIT_TEST)?.lowerBoundVersion)
            .isEqualTo("4.12".version)
          expect
            .that(module.getResolvedDependency("com.android.support.test.espresso:espresso-core:+".gradleCoordinate, UNIT_TEST)?.lowerBoundVersion)
            .isNull()

          expect
            .that(module.getResolvedDependency("com.google.guava:guava:+".gradleCoordinate, ANDROID_TEST)?.lowerBoundVersion)
            .isEqualTo("19.0".version)
          expect
            .that(module.getResolvedDependency("junit:junit:+".gradleCoordinate, ANDROID_TEST)?.lowerBoundVersion)
            .isEqualTo("4.12".version)
          expect
            .that(module.getResolvedDependency("com.android.support.test.espresso:espresso-core:+".gradleCoordinate, ANDROID_TEST)?.lowerBoundVersion)
            .isEqualTo("3.0.2".version)
        },
      )
  }

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun runTest(root: File, project: Project, expect: Expect) {
    body(project, agpVersion, expect)
  }
}

private val String.gradleCoordinate
  get() =
    GradleCoordinate.parseCoordinateString(this) ?: error("Invalid gradle coordinate: $this")

private val String.version get() = Version.parse(this)

