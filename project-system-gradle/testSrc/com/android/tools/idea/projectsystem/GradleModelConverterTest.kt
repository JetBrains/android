// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.projectsystem

import com.android.ide.common.gradle.model.*
import com.android.ide.common.util.PathString
import com.android.ide.common.util.toPathStrings
import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.projectmodel.AndroidPathType
import com.android.projectmodel.AndroidSubmodule
import com.android.projectmodel.matchAllArtifacts
import com.android.projectmodel.matchArtifactsWith
import com.android.projectmodel.submodulePathOf
import com.android.projectmodel.toConfigPath
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat

/**
 * Tests for [GradleModelConverter]. The setup time for these tests are quite slow since it needs to perform a Gradle sync.
 * In order to avoid doing this multiple times, this whole suite is written as a single "test" that invokes multiple "check"
 * methods. Each "check" method validates a bunch of related invariants on the model.
 */
class GradleModelConverterTest : AndroidGradleTestCase() {
  lateinit var project : IdeAndroidProject
  lateinit var converted: AndroidSubmodule

  override fun setUp() {
    super.setUp()
    loadProject(TestProjectPaths.PROJECT_MODEL_MULTIFLAVOR)
    project = model.androidProject
    converted = project.toSubmodule()
  }

  fun testConversion() {
    // Gradle is allowed to return extra configs for locations that could exist on disk but that aren't actually there.
    // The set of hypothetical configs could change over time. However, it is *required* to return configs for all
    // the locations that actually exist on disk as part of the test case, so we only test for these. This makes the
    // test less brittle if the Gradle plugin adds new source locations in the future.
    assertHasConfigs(
        "*/*/debug/${ARTIFACT_NAME_MAIN}",
        "*/*/release/${ARTIFACT_NAME_MAIN}",
        "free/x86/debug/${ARTIFACT_NAME_MAIN}",
        "*/*/*/${ARTIFACT_NAME_MAIN}",
        "pro/x86/*/${ARTIFACT_NAME_MAIN}"
    )
    checkProjectAttributes()
    checkBuildTypeConfigurations()
    checkFlavorConfigurations()
    checkVariants()
  }

  fun checkProjectAttributes() {
    assertThat(converted.name).isEqualTo(project.name)
    assertThat(converted.type).isEqualTo(getProjectType(project.projectType))
  }

  fun assertHasConfigs(vararg expectedPaths:String) {
    for (path in expectedPaths) {
      val configPath = matchArtifactsWith(path)
      assertTrue("Config table should contain exactly 1 config for $path", converted.configTable.filter { it.path == configPath }.configs.size == 1)
    }
  }

  fun checkBuildTypeConfigurations() {
    with(converted.configTable) {
      val debugPath = schema.pathFor("debug")
      val debugAssociations = filter { debugPath.contains(it.path) }
      val debugConfigs = debugAssociations.configs

      with(debugConfigs[0]) {
        with(manifestValues) {
          assertThat(debuggable).isTrue()
          assertThat(compileSdkVersion).isNull()
          assertThat(applicationId).isNull()
        }
        assertThat(sources[AndroidPathType.ASSETS][0].portablePath.endsWith("src/debug/assets")).isTrue()
      }
    }
  }

  fun checkFlavorConfigurations() {
    with(converted.configTable) {
      val x86Path = schema.pathFor("x86")
      val x86Configs = filter { x86Path.contains(it.path) }.configs

      // We've created source folders for freeX86Debug and proX86, so there should be at least 2 x86 configs in the model.
      // There will likely be more since the model may create additional model entries for potential source locations that
      // don't actually exist on disk. These hypothetical locations differ between Gradle plugin versions.
      assertThat(x86Configs.size).isGreaterThan(1)

      val mainArtifactPath = x86Path.intersect(schema.pathFor(ARTIFACT_NAME_MAIN))
      val mainArtifactX86Configs = filter { mainArtifactPath.contains(it.path) }.configs

      // Two of the source folders were for the main config. We should be able to find them in the model.
      assertThat(mainArtifactX86Configs.size).isGreaterThan(1)

      with(mainArtifactX86Configs[0]) {
        with(manifestValues) {
          assertThat(debuggable).isNull()
          assertThat(compileSdkVersion).isNull()
          assertThat(applicationId).isNull()
        }
        assertThat(sources.get(AndroidPathType.MANIFEST)[0].portablePath.endsWith("src/x86/AndroidManifest.xml")).isTrue()
      }
    }
  }

  fun checkVariants() {
    with(converted) {
      val variantPathList = configTable.schema.allVariantPaths().toList()
      // (release, debug) * (x86, arm) * (free, pro) = 2 * 2 * 2 = 8
      assertThat(variantPathList.size).isEqualTo(8)
      val originalVariant = firstVariant()
      val originalTestArtifact = originalVariant.extraAndroidArtifacts.iterator().next()!!
      val originalArtifact = originalVariant.mainArtifact

      val freeX86DebugVariantPath = submodulePathOf("free", "x86", "debug")
      val variant = getVariantByPath(freeX86DebugVariantPath)!!
      val mainArtifact = getArtifact(freeX86DebugVariantPath + ARTIFACT_NAME_MAIN)!!
      val testArtifact = getArtifact(freeX86DebugVariantPath + ARTIFACT_NAME_ANDROID_TEST)!!

      assertThat(variant.name).isEqualTo(freeX86DebugVariantPath.toConfigPath().simpleName)
      with (mainArtifact) {
        assertThat(classFolders).isEqualTo(
          listOf(PathString(originalArtifact.classesFolder)) + originalArtifact.additionalClassesFolders.toPathStrings())
        assertThat(packageName).isEqualTo(project.defaultConfig.productFlavor.applicationId)
        with(resolved) {
          assertThat(usingSupportLibVectors).isFalse()
          with(manifestValues) {
            assertThat(applicationId).isEqualTo(originalArtifact.applicationId)
          }
          // At minimum, it should contain the manifests that were created on disk for this test: main, debug, and freeX86Debug
          assertThat(sources[AndroidPathType.MANIFEST].size).isGreaterThan(2)
        }
      }

      with (testArtifact) {
        assertThat(classFolders).isEqualTo(
            listOf(PathString(originalTestArtifact.classesFolder)) + originalTestArtifact.additionalClassesFolders.toPathStrings())
        // Note that we don't currently fill in the package name for test artifacts, since the gradle model doesn't return it
        with(resolved) {
          assertThat(usingSupportLibVectors).isFalse()
          with(manifestValues) {
            assertThat(applicationId).isEqualTo(originalTestArtifact.applicationId)
          }
        }
      }
    }
  }

  private fun firstVariant(): IdeVariant {
    var originalVariantVar: IdeVariant? = null
    project.forEachVariant {
      if (originalVariantVar == null) {
        originalVariantVar = it
      }
    }
    return originalVariantVar!!
  }
}