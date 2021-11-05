/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.tools.idea.gradle.structure.configurables.PsPathRenderer
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModuleDefaultConfigDescriptors
import com.android.tools.idea.gradle.structure.model.android.PsBuildType
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.android.moduleWithSyncedModel
import com.android.tools.idea.gradle.structure.model.android.moduleWithoutSyncedModel
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.parents
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.util.SystemInfoRt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat

class PsAndroidModuleVariantsAnalyzerTest : DependencyTestCase() {

  private val pathRenderer = object : PsPathRenderer {
    override fun PsPath.renderNavigation(specificPlace: PsPath): String {
      fun PsPath.toTestName(upTo: PsPath? = null) =
        (parents + this)
          .dropWhile { upTo != null && it != upTo }
          .joinToString("/") { if (upTo != null && it == upTo) "." else it.toString() }

      return "<${this.toTestName()}> [${specificPlace.toTestName(this)}]"
    }
  }

  fun testNoIssues() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    val result = analyzeModuleDependencies(appModule, pathRenderer).toList()
    assertThat(result, equalTo(emptyList()))
  }

  fun testNoMatchingBuildTypeInTarget() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    val mainModule = moduleWithoutSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())

    appModule.addNewBuildType("newBuildType")

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(listOf(
      "ERROR: No build type in module '<:mainModule> [./Build Variants/Build Types]' " +
      "matches build type '<:app/Build Variants/Build Types/newBuildType> [.]'."
    )))
  }

  fun testReleaseAndDebugBuildTypeMatchesUndeclaredBeforeModelsAreFetched() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject) // Do NOT resolve models.

    val appModule = moduleWithSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())
    assumeThat(appModule.findBuildType("debug"), notNullValue())
    assumeThat(appModule.findBuildType("release"), notNullValue())

    val mainModule = moduleWithSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())
    assumeThat(mainModule.findBuildType("debug"), notNullValue())
    assumeThat(mainModule.findBuildType("release"), notNullValue())

    mainModule.findBuildType("release")!!.debuggable = false.asParsed()  // Ensure explicitly declared.
    mainModule.findBuildType("debug")!!.debuggable = true.asParsed()  // Ensure explicitly declared.

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(listOf()))
  }

  fun testReleaseAndDebugBuildTypeMatchesUndeclaredAfterModelsAreFetched() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() } // DO fetch models.

    val appModule = moduleWithSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())
    assumeThat(appModule.findBuildType("debug")?.isDeclared, equalTo(true))
    assumeThat(appModule.findBuildType("release")?.isDeclared, equalTo(true))

    val mainModule = moduleWithSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())
    assumeThat(mainModule.findBuildType("debug")?.isDeclared, equalTo(true))
    assumeThat(mainModule.findBuildType("release")?.isDeclared, equalTo(true))

    val releaseBuildType = appModule.findBuildType("release")!!
    releaseBuildType.debuggable = false.asParsed()  // Declare in config.
    val debugBuildType = appModule.findBuildType("debug")!!
    debugBuildType.debuggable = true.asParsed()  // Declare in config.

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(listOf()))
  }

  fun testNoMatchingBuildTypeInTargetButFallback() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    val mainModule = moduleWithoutSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())

    val newBuildType = appModule.addNewBuildType("newBuildType")
    PsBuildType.BuildTypeDescriptors
      .matchingFallbacks.bind(newBuildType)
      .addItem(0).setParsedValue("debug".asParsed())

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(emptyList()))
  }

  fun testDebugBuildTypeAlwaysMatches() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    val mainModule = moduleWithoutSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())

    assumeThat(appModule.findBuildType("debug"), notNullValue())

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(emptyList()))
  }

  fun testNoMatchingDimensionInTarget() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    val mainModule = moduleWithoutSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())

    appModule.addNewFlavorDimension("foo")
    appModule.addNewProductFlavor("foo", "newProductFlavor")

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(emptyList()))
  }

  fun testNotMatchingProductFlavorInTarget() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    val mainModule = moduleWithoutSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())

    appModule.addNewFlavorDimension("foo")
    appModule.addNewProductFlavor("foo", "newProductFlavor")
    mainModule.addNewFlavorDimension("foo")

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(listOf(
      "ERROR: No product flavor in module '<:mainModule> [./Build Variants/Product Flavors]' " +
      "matches product flavor '<:app/Build Variants/Product Flavors/newProductFlavor> [.]' " +
      "in dimension '<:app/Build Variants/Product Flavors/foo> [.]'."
    )))
  }

  fun testNotMatchingProductFlavorInTargetButFallback() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    val mainModule = moduleWithoutSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())

    appModule.addNewFlavorDimension("foo")
    val newProductFlavor = appModule.addNewProductFlavor("foo", "newProductFlavor")
    mainModule.addNewFlavorDimension("foo")
    mainModule.addNewProductFlavor("foo", "fallback")
    PsProductFlavor.ProductFlavorDescriptors
      .matchingFallbacks.bind(newProductFlavor)
      .addItem(0)
      .setParsedValue("fallback".asParsed())

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(emptyList()))
  }

  fun testNoMatchingDimensionInSourceAndSingleFlavorInTarget() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    val mainModule = moduleWithoutSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())

    mainModule.addNewFlavorDimension("foo")
    mainModule.addNewProductFlavor("foo", "newProductFlavor")

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(emptyList()))
  }

  fun testNoMatchingDimensionInSourceAndMultipleFlavorsInTarget() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    val mainModule = moduleWithoutSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())

    mainModule.addNewFlavorDimension("foo")
    mainModule.addNewProductFlavor("foo", "newProductFlavor")
    mainModule.addNewProductFlavor("foo", "anotherNewProductFlavor")

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(listOf(
      "ERROR: No flavor dimension in module '<:app> [./Build Variants/Product Flavors]' " +
      "matches dimension '<:mainModule/Build Variants/Product Flavors/foo> [.]' " +
      "from module <:mainModule> [./Build Variants/Product Flavors] " +
      "on which module '<:app> [./Dependencies/mainModule]' depends."
    )))
  }

  fun testNoMatchingDimensionInSourceAndMultipleFlavorsInTargetButMissingDimensionStrategy() {
    if (SystemInfoRt.isWindows) return  // b/149874781

    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())
    // TOdO(b/79563663): Edit missingDimensionStrategy via Ps-* properties when implemented.
    PsAndroidModuleDefaultConfigDescriptors.getParsed(appModule.defaultConfig)
      ?.addMissingDimensionStrategy("foo", "newProductFlavor")

    val mainModule = moduleWithoutSyncedModel(project, "mainModule")
    assumeThat(mainModule, notNullValue())

    mainModule.addNewFlavorDimension("foo")
    mainModule.addNewProductFlavor("foo", "newProductFlavor")
    mainModule.addNewProductFlavor("foo", "anotherNewProductFlavor")

    val result = analyzeModuleDependencies(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(emptyList()))
  }

  fun testNoFlavorDimensionWithOneDimension() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    assumeThat(appModule.flavorDimensions.size, equalTo(1))
    appModule.addNewProductFlavor("foo", "newProductFlavor").configuredDimension = ParsedValue.NotSet

    val result = analyzeProductFlavors(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(emptyList()))
  }

  fun testNoFlavorDimensionWithMultipleDimensions() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    assumeThat(appModule.flavorDimensions.size, equalTo(1))
    appModule.addNewFlavorDimension("dim2")
    appModule.addNewProductFlavor("foo", "newProductFlavor").configuredDimension = ParsedValue.NotSet

    val result = analyzeProductFlavors(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(listOf(
      "ERROR: Flavor '<:app/Build Variants/Product Flavors/newProductFlavor> [.]' has no flavor dimension."
    )))
  }

  fun testUnknownFlavorDimension() {
    loadProject(TestProjectPaths.PSD_DEPENDENCY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assumeThat(appModule, notNullValue())

    appModule.addNewProductFlavor("foo", "newProductFlavor")

    val result = analyzeProductFlavors(appModule, pathRenderer).map { it.toString() }.toList()
    assertThat(result, equalTo(listOf(
      "ERROR: Flavor '<:app/Build Variants/Product Flavors/newProductFlavor> [.]' has unknown dimension 'foo'."
    )))
  }
}