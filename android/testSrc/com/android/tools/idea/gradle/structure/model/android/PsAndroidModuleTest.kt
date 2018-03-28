/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.testing.TestProjectPaths.*
import com.google.common.collect.Lists
import com.google.common.truth.Truth.assertThat
import java.io.File

/**
 * Tests for [PsAndroidModule].
 */
class PsAndroidModuleTest : DependencyTestCase() {

  fun testFlavorDimensions() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    val flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar").inOrder()
  }

  fun testAddFlavorDimension() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    appModule.addNewFlavorDimension("new")
    // A product flavor is required for successful sync.
    val newInNew = appModule.addNewProductFlavor("new_in_new")
    newInNew.dimension = ParsedValue.Set.Parsed("new", null)
    appModule.applyChanges()

    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    val flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar", "new").inOrder()
  }

  fun testRemoveFlavorDimension() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    appModule.removeFlavorDimension("bar")
    // A product flavor must be removed for successful sync.
    appModule.removeProductFlavor(appModule.findProductFlavor("bar")!!)
    var flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions).containsExactly("foo", "bar").inOrder()
    appModule.applyChanges()

    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions).containsExactly("foo")
  }

  private fun getFlavorDimensions(module: PsAndroidModule): List<String> {
    return Lists.newArrayList(module.flavorDimensions)
  }

  fun testProductFlavors() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    val productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid").inOrder()
    assertThat(productFlavors).hasSize(2)

    val basic = appModule.findProductFlavor("basic")
    assertNotNull(basic)
    assertTrue(basic!!.isDeclared)

    val release = appModule.findProductFlavor("paid")
    assertNotNull(release)
    assertTrue(release!!.isDeclared)
  }

  fun testAddProductFlavor() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    var productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid").inOrder()

    appModule.addNewProductFlavor("new_flavor")

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid", "new_flavor").inOrder()

    var newFlavor = appModule.findProductFlavor("new_flavor")
    assertNotNull(newFlavor)
    assertNull(newFlavor!!.resolvedModel)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid", "new_flavor").inOrder()

    newFlavor = appModule.findProductFlavor("new_flavor")
    assertNotNull(newFlavor)
    assertNotNull(newFlavor!!.resolvedModel)
  }

  fun testRemoveProductFlavor() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    var productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid", "bar").inOrder()

    appModule.removeProductFlavor(appModule.findProductFlavor("paid")!!)

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "bar").inOrder()

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "bar").inOrder()
  }

  fun testBuildTypes() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    val buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "debug").inOrder()
    assertThat(buildTypes).hasSize(2)

    val release = appModule.findBuildType("release")
    assertNotNull(release)
    assertTrue(release!!.isDeclared)

    val debug = appModule.findBuildType("debug")
    assertNotNull(debug)
    assertTrue(!debug!!.isDeclared)
  }

  fun testAddBuildType() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    var buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "debug").inOrder()

    appModule.addNewBuildType("new_build_type")

    buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "debug", "new_build_type").inOrder()

    var newBuildType = appModule.findBuildType("new_build_type")
    assertNotNull(newBuildType)
    assertNull(newBuildType!!.resolvedModel)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "new_build_type", "debug").inOrder()  // "debug" is not declared and goes last.

    newBuildType = appModule.findBuildType("new_build_type")
    assertNotNull(newBuildType)
    assertNotNull(newBuildType!!.resolvedModel)
  }

  fun testRemoveBuildType() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    var buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "debug").inOrder()

    appModule.removeBuildType(appModule.findBuildType("release")!!)

    buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("debug")

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("debug", "release").inOrder()  // "release" is not declared and goes last.

    val release = appModule.findBuildType("release")
    assertNotNull(release)
    assertFalse(release!!.isDeclared)
  }

  fun testVariants() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    val variants = appModule.variants
    assertThat(variants).hasSize(4)

    val paidDebug = appModule.findVariant("paidDebug")
    assertNotNull(paidDebug)
    var flavors = paidDebug!!.productFlavors
    assertThat(flavors).containsExactly("paid")

    val paidRelease = appModule.findVariant("paidRelease")
    assertNotNull(paidRelease)
    flavors = paidRelease!!.productFlavors
    assertThat(flavors).containsExactly("paid")

    val basicDebug = appModule.findVariant("basicDebug")
    assertNotNull(basicDebug)
    flavors = basicDebug!!.productFlavors
    assertThat(flavors).containsExactly("basic")

    val basicRelease = appModule.findVariant("basicRelease")
    assertNotNull(basicRelease)
    flavors = basicRelease!!.productFlavors
    assertThat(flavors).containsExactly("basic")
  }

  fun testCanDependOnModules() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    val libModule = project.findModuleByName("lib") as PsAndroidModule?
    assertNotNull(libModule)

    assertTrue(appModule.canDependOn(libModule!!))
    assertFalse(libModule.canDependOn(appModule))
  }

  fun testSigningConfigs() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    val signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs).hasSize(2)

    val myConfig = appModule.findSigningConfig("myConfig")
    assertNotNull(myConfig)
    assertTrue(myConfig!!.isDeclared)

    val debugConfig = appModule.findSigningConfig("debug")
    assertNotNull(debugConfig)
    assertTrue(!debugConfig!!.isDeclared)
  }

  fun testAddSigningConfig() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    var signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "debug").inOrder()

    val myConfig = appModule.addNewSigningConfig("config2")
    myConfig.storeFile = ParsedValue.Set.Parsed(File("/tmp/1"), null)

    assertNotNull(myConfig)
    assertTrue(myConfig.isDeclared)

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "debug", "config2").inOrder()

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "config2", "debug").inOrder()
  }

  fun testRemoveSigningConfig() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    var signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "debug").inOrder()

    appModule.removeSigningConfig(appModule.findSigningConfig("myConfig")!!)
    appModule.removeBuildType(appModule.findBuildType("debug")!!)  // Remove (clean) the build type that refers to the signing config.

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("debug")

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProject(resolvedProject)
    appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("debug")
  }
}
