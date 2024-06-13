/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage.runsGradle

import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.APK
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.BUNDLE
import org.junit.Rule
import org.junit.Test

class ExportSignedPackageWizardTest {
  @get:Rule
  var myRule = AndroidGradleProjectRule()

  @Test
  fun testNoFlavors() {
    myRule.loadProject(TestProjectPaths.SIGNAPK_NO_FLAVORS)
    val facet = myRule.androidFacet(":")
    val androidModel = GradleAndroidModel.Companion.get(facet)
    assertThat(androidModel).isNotNull()
    val androidProject: IdeAndroidProject = androidModel!!.androidProject
    assertThat(androidProject).isNotNull()

    // debug and release
    assertThat(androidProject.basicVariants).hasSize(2)
    val assembleTasks = ExportSignedPackageWizard.getGradleTasks("", androidModel, listOf("release"), APK)
    assertThat(assembleTasks).hasSize(1)
    assertThat(assembleTasks[0]).isEqualTo(":assembleRelease")
  }

  @Test
  fun testFlavors() {
    myRule.loadProject(TestProjectPaths.SIGNAPK_MULTIFLAVOR)
    val facet = myRule.androidFacet(":")
    val androidModel = GradleAndroidModel.Companion.get(facet)
    assertThat(androidModel).isNotNull()
    val androidProject: IdeAndroidProject = androidModel!!.androidProject
    assertThat(androidProject).isNotNull()

    // (free,pro) x (arm,x86) x (debug,release) = 8
    assertThat(androidProject.basicVariants).hasSize(8)
    val assembleTasks = ExportSignedPackageWizard.getGradleTasks("", androidModel, mutableListOf("proX86Release", "freeArmRelease"), APK)
    assertThat(assembleTasks).containsExactly(":assembleProX86Release", ":assembleFreeArmRelease")
  }

  @Test
  fun testBundleNoFlavors() {
    myRule.loadProject(TestProjectPaths.SIGNAPK_NO_FLAVORS)
    val facet = myRule.androidFacet(":")
    val androidModel = GradleAndroidModel.Companion.get(facet)
    assertThat(androidModel).isNotNull()
    val androidProject: IdeAndroidProject = androidModel!!.androidProject
    assertThat(androidProject).isNotNull()

    // debug and release
    assertThat(androidProject.basicVariants).hasSize(2)
    val assembleTasks = ExportSignedPackageWizard.getGradleTasks("", androidModel, listOf("release"), BUNDLE)
    assertThat(assembleTasks).containsExactly(":bundleRelease")
  }

  @Test
  fun testBundleFlavors() {
    myRule.loadProject(TestProjectPaths.SIGNAPK_MULTIFLAVOR)
    val facet = myRule.androidFacet(":")
    val androidModel = GradleAndroidModel.Companion.get(facet)
    assertThat(androidModel).isNotNull()
    val androidProject: IdeAndroidProject = androidModel!!.androidProject
    assertThat(androidProject).isNotNull()

    // (free,pro) x (arm,x86) x (debug,release) = 8
    assertThat(androidProject.basicVariants).hasSize(8)
    val assembleTasks = ExportSignedPackageWizard.getGradleTasks("", androidModel, mutableListOf("proX86Release", "freeArmRelease"), BUNDLE)
    assertThat(assembleTasks).containsExactly(":bundleProX86Release", ":bundleFreeArmRelease")
  }
}
