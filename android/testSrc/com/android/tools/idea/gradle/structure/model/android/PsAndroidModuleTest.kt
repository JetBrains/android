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

import com.android.tools.idea.gradle.structure.GradleResolver
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.testing.TestProjectPaths.BASIC
import com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE
import com.android.tools.idea.testing.TestProjectPaths.SCRIPTED_DIMENSIONS
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tests for [PsAndroidModule].
 */
class PsAndroidModuleTest : DependencyTestCase() {
  val changedModules = mutableSetOf<String>()
  var buildTypesChanged = 0
  var productFlavorsChanged = 0
  var flavorDimensionsChanged = 0
  var signingConfigsChanged = 0
  var variantsChanged = 0

  private val DISALLOWED_NAME_CHARS = "/\\:<>\"?*|"

  fun testFlavorDimensions() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule);

    val flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar").inOrder()
  }

  fun testFallbackFlavorDimensions() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assertNotNull(appModule);

    val flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar").inOrder()
  }

  fun testScriptedFlavorDimensions() {
    loadProject(SCRIPTED_DIMENSIONS)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    run {
      val appModule = moduleWithoutSyncedModel(project, "app")
      assertNotNull(appModule);

      val flavorDimensions = getFlavorDimensions(appModule)
      assertThat(flavorDimensions).isEmpty()
    }

    run {
      val appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule);

      val flavorDimensions = getFlavorDimensions(appModule)
      assertThat(flavorDimensions)
        .containsExactly("dimScripted").inOrder()
    }
  }

  fun testValidateFlavorDimensionName() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assertNotNull(appModule);

    assertThat(appModule.validateFlavorDimensionName("")).isEqualTo("Flavor dimension name cannot be empty.")
    assertThat(appModule.validateFlavorDimensionName("foo")).isEqualTo("Duplicate flavor dimension name: 'foo'")
    assertThat(appModule.validateFlavorDimensionName("ok")).isNull()
    DISALLOWED_NAME_CHARS.forEach {
      assertThat(appModule.validateFlavorDimensionName("${it}")).isNotNull()
      assertThat(appModule.validateFlavorDimensionName("foo${it}")).isNotNull()
      assertThat(appModule.validateFlavorDimensionName("foo${it}bar")).isNotNull()
      assertThat(appModule.validateFlavorDimensionName("'${it}'")).isNotNull()
      assertThat(appModule.validateFlavorDimensionName("''${it}''")).isNotNull()
    }
  }

  fun testAddFlavorDimension() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)
    appModule.testSubscribeToChangeNotifications()

    appModule.addNewFlavorDimension("new")
    assertThat(flavorDimensionsChanged).isEqualTo(1)
    // A product flavor is required for successful sync.
    val newInNew = appModule.addNewProductFlavor("new", "new_in_new")
    appModule.applyChanges()

    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    val flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar", "new").inOrder()
  }

  fun testAddFirstFlavorDimension() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "lib")
    assertNotNull(appModule)
    appModule.testSubscribeToChangeNotifications()

    appModule.addNewFlavorDimension("bar")
    assertThat(flavorDimensionsChanged).isEqualTo(1)
    // A product flavor is required for successful sync.
    val bar = appModule.addNewProductFlavor("bar", "bar")
    val otherBar = appModule.addNewProductFlavor("bar", "otherBar")
    appModule.applyChanges()

    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "lib")
    assertNotNull(appModule)

    val flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions).containsExactly("bar")
  }

  fun testRemoveFlavorDimension() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)
    appModule.testSubscribeToChangeNotifications()

    // A product flavor must be removed for successful sync.
    appModule.removeProductFlavor(appModule.findProductFlavor("bar", "bar")!!)
    appModule.removeProductFlavor(appModule.findProductFlavor("bar", "otherBar")!!)

    appModule.removeFlavorDimension(appModule.findFlavorDimension("bar")!!)
    assertThat(flavorDimensionsChanged).isEqualTo(1)
    var flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions).containsExactly("foo").inOrder()
    appModule.applyChanges()

    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions).containsExactly("foo")
  }

  private fun getFlavorDimensions(module: PsAndroidModule): List<String> = module.flavorDimensions.map { it.name }

  fun testProductFlavors() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    val productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid").inOrder()
    assertThat(productFlavors).hasSize(2)

    val basic = appModule.findProductFlavor("foo", "basic")
    assertNotNull(basic)
    assertTrue(basic!!.isDeclared)

    val release = appModule.findProductFlavor("foo", "paid")
    assertNotNull(release)
    assertTrue(release!!.isDeclared)
  }

  fun testFallbackProductFlavors() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assertNotNull(appModule)

    val productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid").inOrder()
    assertThat(productFlavors).hasSize(2)

    val basic = appModule.findProductFlavor("foo", "basic")
    assertNotNull(basic)
    assertTrue(basic!!.isDeclared)

    val release = appModule.findProductFlavor("foo", "paid")
    assertNotNull(release)
    assertTrue(release!!.isDeclared)
  }

  fun testValidateProductFlavorName() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assertNotNull(appModule);

    assertThat(appModule.validateProductFlavorName("")).isEqualTo("Product flavor name cannot be empty.")
    assertThat(appModule.validateProductFlavorName("paid")).isEqualTo("Duplicate product flavor name: 'paid'")
    assertThat(appModule.validateProductFlavorName("ok")).isNull()
    DISALLOWED_NAME_CHARS.forEach {
      assertThat(appModule.validateProductFlavorName("${it}")).isNotNull()
      assertThat(appModule.validateProductFlavorName("foo${it}")).isNotNull()
      assertThat(appModule.validateProductFlavorName("foo${it}bar")).isNotNull()
      assertThat(appModule.validateProductFlavorName("'${it}'")).isNotNull()
      assertThat(appModule.validateProductFlavorName("''${it}''")).isNotNull()
    }
  }

  fun testAddProductFlavor() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    appModule.testSubscribeToChangeNotifications()
    assertNotNull(appModule)

    var productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid").inOrder()

    appModule.addNewProductFlavor("foo", "new_flavor")
    assertThat(productFlavorsChanged).isEqualTo(1)

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid", "new_flavor").inOrder()

    var newFlavor = appModule.findProductFlavor("foo", "new_flavor")
    assertNotNull(newFlavor)
    assertNull(newFlavor!!.resolvedModel)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid", "new_flavor").inOrder()

    newFlavor = appModule.findProductFlavor("foo", "new_flavor")
    assertNotNull(newFlavor)
    assertNotNull(newFlavor!!.resolvedModel)
  }

  fun testRemoveProductFlavor() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    appModule.testSubscribeToChangeNotifications()
    assertNotNull(appModule)

    var productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid", "bar", "otherBar").inOrder()

    appModule.removeProductFlavor(appModule.findProductFlavor("foo", "paid")!!)
    assertThat(productFlavorsChanged).isEqualTo(1)

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "bar", "otherBar").inOrder()

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "bar", "otherBar").inOrder()
  }

  fun testRenameProductFlavor() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    appModule.testSubscribeToChangeNotifications()
    assertNotNull(appModule)

    var productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid", "bar", "otherBar").inOrder()

    appModule.findProductFlavor("foo", "paid")!!.rename("paidLittle")
    assertThat(productFlavorsChanged).isEqualTo(1)

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paidLittle", "bar", "otherBar").inOrder()

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paidLittle", "bar", "otherBar").inOrder()
  }

  fun testBuildTypes() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val libModule = moduleWithSyncedModel(project, "lib")
    assertNotNull(libModule)

    val buildTypes = libModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "debug").inOrder()
    assertThat(buildTypes).hasSize(2)

    val release = libModule.findBuildType("release")
    assertNotNull(release)
    assertTrue(release!!.isDeclared)

    val debug = libModule.findBuildType("debug")
    assertNotNull(debug)
    assertTrue(!debug!!.isDeclared)
  }

  fun testFallbackBuildTypes() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val libModule = moduleWithoutSyncedModel(project, "lib")
    assertNotNull(libModule)

    val buildTypes = libModule.buildTypes
    assertThat(buildTypes.map { it.name }).containsExactly("release")
    assertThat(buildTypes).hasSize(1)

    val release = libModule.findBuildType("release")
    assertNotNull(release)
    assertTrue(release!!.isDeclared)
  }

  fun testValidateBuildTypeName() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assertNotNull(appModule);

    assertThat(appModule.validateBuildTypeName("")).isEqualTo("Build type name cannot be empty.")
    assertThat(appModule.validateBuildTypeName("specialRelease")).isEqualTo("Duplicate build type name: 'specialRelease'")
    assertThat(appModule.validateBuildTypeName("ok")).isNull()
    DISALLOWED_NAME_CHARS.forEach {
      assertThat(appModule.validateBuildTypeName("${it}")).isNotNull()
      assertThat(appModule.validateBuildTypeName("foo${it}")).isNotNull()
      assertThat(appModule.validateBuildTypeName("foo${it}bar")).isNotNull()
      assertThat(appModule.validateBuildTypeName("'${it}'")).isNotNull()
      assertThat(appModule.validateBuildTypeName("''${it}''")).isNotNull()
    }
  }

  fun testAddBuildType() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    appModule.testSubscribeToChangeNotifications()
    assertNotNull(appModule)

    var buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "debug").inOrder()

    appModule.addNewBuildType("new_build_type")
    assertThat(buildTypesChanged).isEqualTo(1)

    buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "debug", "new_build_type").inOrder()

    var newBuildType = appModule.findBuildType("new_build_type")
    assertNotNull(newBuildType)
    assertNull(newBuildType!!.resolvedModel)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

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
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    appModule.testSubscribeToChangeNotifications()
    assertNotNull(appModule)

    var buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "specialRelease", "debug").inOrder()

    appModule.removeBuildType(appModule.findBuildType("release")!!)
    assertThat(buildTypesChanged).isEqualTo(1)

    buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("specialRelease", "debug")

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("specialRelease", "debug", "release").inOrder()  // "release" is not declared and goes last.

    val release = appModule.findBuildType("release")
    assertNotNull(release)
    assertFalse(release!!.isDeclared)
  }

  fun testRenameBuildType() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    appModule.testSubscribeToChangeNotifications()
    assertNotNull(appModule)

    var buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "specialRelease", "debug").inOrder()

    appModule.findBuildType("release")!!.rename("almostRelease")
    assertThat(buildTypesChanged).isEqualTo(1)

    buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("almostRelease", "specialRelease", "debug")

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("almostRelease", "specialRelease", "debug", "release").inOrder()  // "release" is not declared and goes last.

    val release = appModule.findBuildType("release")
    assertNotNull(release)
    assertFalse(release!!.isDeclared)
  }

  fun testVariants() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    val variants = appModule.resolvedVariants
    assertThat(variants).hasSize(4)

    val paidDebug = appModule.findVariant("paidDebug")
    assertNotNull(paidDebug)
    var flavors = paidDebug!!.productFlavorNames
    assertThat(flavors).containsExactly("paid")

    val paidRelease = appModule.findVariant("paidRelease")
    assertNotNull(paidRelease)
    flavors = paidRelease!!.productFlavorNames
    assertThat(flavors).containsExactly("paid")

    val basicDebug = appModule.findVariant("basicDebug")
    assertNotNull(basicDebug)
    flavors = basicDebug!!.productFlavorNames
    assertThat(flavors).containsExactly("basic")

    val basicRelease = appModule.findVariant("basicRelease")
    assertNotNull(basicRelease)
    flavors = basicRelease!!.productFlavorNames
    assertThat(flavors).containsExactly("basic")
  }

  fun testCanDependOnModules() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    val libModule = moduleWithSyncedModel(project, "lib")
    assertNotNull(libModule)

    assertTrue(appModule.canDependOn(libModule))
    assertFalse(libModule.canDependOn(appModule))
  }

  fun testSigningConfigs() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

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

  fun testValidateSigningConfigName() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithoutSyncedModel(project, "app")
    assertNotNull(appModule);

    assertThat(appModule.validateSigningConfigName("")).isEqualTo("Signing config name cannot be empty.")
    assertThat(appModule.validateSigningConfigName("myConfig")).isEqualTo("Duplicate signing config name: 'myConfig'")
    assertThat(appModule.validateSigningConfigName("ok")).isNull()
    DISALLOWED_NAME_CHARS.forEach {
      assertThat(appModule.validateSigningConfigName("${it}")).isNotNull()
      assertThat(appModule.validateSigningConfigName("foo${it}")).isNotNull()
      assertThat(appModule.validateSigningConfigName("foo${it}bar")).isNotNull()
      assertThat(appModule.validateSigningConfigName("'${it}'")).isNotNull()
      assertThat(appModule.validateSigningConfigName("''${it}''")).isNotNull()
    }
  }

  fun testAddSigningConfig() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!
    appModule.testSubscribeToChangeNotifications()

    var signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "debug").inOrder()

    val myConfig = appModule.addNewSigningConfig("config2")
    assertThat(signingConfigsChanged).isEqualTo(1)
    myConfig.storeFile = ParsedValue.Set.Parsed(File("/tmp/1"), DslText.Literal)

    assertNotNull(myConfig)
    assertTrue(myConfig.isDeclared)

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "debug", "config2").inOrder()

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "config2", "debug").inOrder()
  }

  fun testRemoveSigningConfig() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!
    appModule.testSubscribeToChangeNotifications()

    var signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "debug").inOrder()

    appModule.removeSigningConfig(appModule.findSigningConfig("myConfig")!!)
    assertThat(signingConfigsChanged).isEqualTo(1)
    appModule.removeBuildType(appModule.findBuildType("debug")!!)  // Remove (clean) the build type that refers to the signing config.

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("debug")

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("debug")
  }

  fun testRenameSigningConfig() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!
    appModule.testSubscribeToChangeNotifications()

    var signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "debug").inOrder()

    appModule.findSigningConfig("myConfig")!!.rename("yourConfig")
    assertThat(signingConfigsChanged).isEqualTo(1)
    appModule.removeBuildType(appModule.findBuildType("debug")!!)  // Remove (clean) the build type that refers to the signing config.

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("yourConfig", "debug")

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("yourConfig", "debug")
  }

  fun testApplyChangesDropsResolvedValues() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    appModule.buildTypes.toList()[0].jniDebuggable = true.asParsed()
    project.applyChanges()
    assertThat(appModule).isSameAs(project.findModuleByGradlePath(":") as PsAndroidModule?)

    assertThat(appModule.resolvedModel).isNull()
    appModule.buildTypes.forEach { buildType ->
      assertThat(buildType.resolvedModel).isNull()
    }
    appModule.productFlavors.forEach { productFlavor ->
      assertThat(productFlavor.resolvedModel).isNull()
    }
    appModule.signingConfigs.forEach { signingConfig ->
      assertThat(signingConfig.resolvedModel).isNull()
    }
    // TODO(b/110194207): Populate variant collection when unsynced.
    assertThat(appModule.resolvedVariants).isEmpty()
  }

  fun testApplyChangesAndSyncReloadsResolvedValues() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    (project.findModuleByGradlePath(":app") as PsAndroidModule?).let { appModule ->
      assertNotNull(appModule); appModule!!

      val debugBuildType = appModule.buildTypes.find { it.name == "debug" }!!
      debugBuildType.jniDebuggable = true.asParsed()
      project.applyChanges()
      FileDocumentManager.getInstance().saveAllDocuments()
      val resolver = GradleResolver()
      val disposable = Disposer.newDisposable()
      try {
        val resolved = resolver.requestProjectResolved(project.ideProject, disposable)
        project.refreshFrom(resolved.get(30, TimeUnit.SECONDS))

        assertThat(appModule).isSameAs(project.findModuleByGradlePath(":app") as PsAndroidModule?)
        assertThat(debugBuildType).isSameAs(appModule.buildTypes.find { it.name == "debug" })

        assertThat(debugBuildType.jniDebuggable).isEqualTo(true.asParsed())
        assertThat(PsBuildType.BuildTypeDescriptors.jniDebuggable.bind(debugBuildType).getValue().annotation).isNull()

        assertThat(appModule.buildTypes.map { it.name }).containsExactly("debug", "release", "specialRelease")
        assertThat(appModule.productFlavors.map { it.name }).containsExactly("basic", "paid", "bar", "otherBar")
        assertThat(appModule.signingConfigs.map { it.name }).containsExactly("myConfig", "debug")
        assertThat(appModule.dependencies.items.map { "${it.joinedConfigurationNames} ${it.name}" })
          .containsExactly("api appcompat-v7", "api libs")
      }
      finally {
        Disposer.dispose(disposable)
      }
    }

    (project.findModuleByGradlePath(":nested2:trans:deep2") as PsAndroidModule?).let { nestedModules ->
      assertNotNull(nestedModules); nestedModules!!

      val debugBuildType = nestedModules.buildTypes.find { it.name == "debug" }!!
      debugBuildType.jniDebuggable = true.asParsed()
      project.applyChanges()
      FileDocumentManager.getInstance().saveAllDocuments()
      val resolver = GradleResolver()
      val disposable = Disposer.newDisposable()
      try {
        val resolved = resolver.requestProjectResolved(project.ideProject, disposable)
        project.refreshFrom(resolved.get(30, TimeUnit.SECONDS))

        assertThat(nestedModules).isSameAs(project.findModuleByGradlePath(":nested2:trans:deep2") as PsAndroidModule?)
        assertThat(debugBuildType).isSameAs(nestedModules.buildTypes.find { it.name == "debug" })

        assertThat(debugBuildType.jniDebuggable).isEqualTo(true.asParsed())
        assertThat(PsBuildType.BuildTypeDescriptors.jniDebuggable.bind(debugBuildType).getValue().annotation).isNull()

        assertThat(nestedModules.buildTypes.map { it.name }).containsExactly("debug", "release")
        assertThat(nestedModules.productFlavors.map { it.name }).isEmpty()
        assertThat(nestedModules.signingConfigs.map { it.name }).containsExactly("debug")
        assertThat(nestedModules.dependencies.items.map { "${it.joinedConfigurationNames} ${it.name}" }).isEmpty()
      }
      finally {
        Disposer.dispose(disposable)
      }
    }
  }

  fun testLoadingRootlessProject() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val module = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(module); module!!

    assertThat(module.buildTypes.map { it.name }).containsExactly("debug", "release")
    assertThat(module.productFlavors.map { it.name }).isEmpty()
    assertThat(module.signingConfigs.map { it.name }).containsExactly("myConfig", "debug")
    assertThat(module.dependencies.items.map { "${it.joinedConfigurationNames} ${it.name}" })
      .containsExactly("debugApi support-v13", "api support-v4")
  }

  fun testResolvingProjectReloadsCollections() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    val appModule = moduleWithSyncedModel(project, "app")

    appModule.testSubscribeToChangeNotifications()
    assertThat(buildTypesChanged).isEqualTo(0)
    assertThat(productFlavorsChanged).isEqualTo(0)
    assertThat(signingConfigsChanged).isEqualTo(0)
    assertThat(variantsChanged).isEqualTo(0)

    project.testResolve()

    assertThat(buildTypesChanged).isEqualTo(1)
    assertThat(productFlavorsChanged).isEqualTo(1)
    assertThat(signingConfigsChanged).isEqualTo(1)
    assertThat(variantsChanged).isEqualTo(1)
  }

  fun testModuleChanged() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    val disposable = Disposer.newDisposable()
    project.testSubscribeToNotifications(disposable)

    val appModule = moduleWithSyncedModel(project, "app")
    val libModule = moduleWithSyncedModel(project, "lib")


    appModule.addNewBuildType("newBuildType")
    libModule.addNewBuildType("newBuildType")

    assertThat(changedModules).containsExactly(":app", ":lib")

    Disposer.dispose(disposable)
    changedModules.clear()

    appModule.removeBuildType(appModule.findBuildType("newBuildType")!!)
    libModule.removeBuildType(libModule.findBuildType("newBuildType")!!)

    assertThat(changedModules).isEmpty()
  }

  fun testImportantConfigurations() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    assertThat(appModule.getConfigurations(onlyImportantFor = PsModule.ImportantFor.LIBRARY)).containsExactly(
      "implementation",
      "releaseImplementation",
      "specialReleaseImplementation",
      "debugImplementation",
      "basicImplementation",
      "basicReleaseImplementation",
      "basicSpecialReleaseImplementation",
      "basicDebugImplementation",
      "paidImplementation",
      "paidReleaseImplementation",
      "paidSpecialReleaseImplementation",
      "paidDebugImplementation",
      "barImplementation",
      "barReleaseImplementation",
      "barSpecialReleaseImplementation",
      "barDebugImplementation",
      "otherBarImplementation",
      "otherBarReleaseImplementation",
      "otherBarSpecialReleaseImplementation",
      "otherBarDebugImplementation",
      "testImplementation",
      "testReleaseImplementation",
      "testSpecialReleaseImplementation",
      "testDebugImplementation",
      "testBasicImplementation",
      "testBasicReleaseImplementation",
      "testBasicSpecialReleaseImplementation",
      "testBasicDebugImplementation",
      "testPaidImplementation",
      "testPaidReleaseImplementation",
      "testPaidSpecialReleaseImplementation",
      "testPaidDebugImplementation",
      "testBarImplementation",
      "testBarReleaseImplementation",
      "testBarSpecialReleaseImplementation",
      "testBarDebugImplementation",
      "testOtherBarImplementation",
      "testOtherBarReleaseImplementation",
      "testOtherBarSpecialReleaseImplementation",
      "testOtherBarDebugImplementation",
      "androidTestImplementation",
      "androidTestBasicImplementation",
      "androidTestPaidImplementation",
      "androidTestBarImplementation",
      "androidTestOtherBarImplementation")

    assertThat(appModule.getConfigurations(onlyImportantFor = PsModule.ImportantFor.MODULE)).containsExactly(
      "implementation",
      "api",
      "testImplementation",
      "testApi",
      "androidTestImplementation",
      "androidTestApi")
  }

  fun testConfigurations() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    assertThat(appModule.getConfigurations()).containsExactly(
      "implementation",
      "releaseImplementation",
      "specialReleaseImplementation",
      "debugImplementation",
      "basicImplementation",
      "basicReleaseImplementation",
      "basicSpecialReleaseImplementation",
      "basicDebugImplementation",
      "paidImplementation",
      "paidReleaseImplementation",
      "paidSpecialReleaseImplementation",
      "paidDebugImplementation",
      "barImplementation",
      "barReleaseImplementation",
      "barSpecialReleaseImplementation",
      "barDebugImplementation",
      "otherBarImplementation",
      "otherBarReleaseImplementation",
      "otherBarSpecialReleaseImplementation",
      "otherBarDebugImplementation",
      "basicBarImplementation",
      "basicBarReleaseImplementation",
      "basicBarSpecialReleaseImplementation",
      "basicBarDebugImplementation",
      "paidBarImplementation",
      "paidBarReleaseImplementation",
      "paidBarSpecialReleaseImplementation",
      "paidBarDebugImplementation",
      "basicOtherBarImplementation",
      "basicOtherBarReleaseImplementation",
      "basicOtherBarSpecialReleaseImplementation",
      "basicOtherBarDebugImplementation",
      "paidOtherBarImplementation",
      "paidOtherBarReleaseImplementation",
      "paidOtherBarSpecialReleaseImplementation",
      "paidOtherBarDebugImplementation",
      "testImplementation",
      "testReleaseImplementation",
      "testSpecialReleaseImplementation",
      "testDebugImplementation",
      "testBasicImplementation",
      "testBasicReleaseImplementation",
      "testBasicSpecialReleaseImplementation",
      "testBasicDebugImplementation",
      "testPaidImplementation",
      "testPaidReleaseImplementation",
      "testPaidSpecialReleaseImplementation",
      "testPaidDebugImplementation",
      "testBarImplementation",
      "testBarReleaseImplementation",
      "testBarSpecialReleaseImplementation",
      "testBarDebugImplementation",
      "testOtherBarImplementation",
      "testOtherBarReleaseImplementation",
      "testOtherBarSpecialReleaseImplementation",
      "testOtherBarDebugImplementation",
      "testBasicBarImplementation",
      "testBasicBarReleaseImplementation",
      "testBasicBarSpecialReleaseImplementation",
      "testBasicBarDebugImplementation",
      "testPaidBarImplementation",
      "testPaidBarReleaseImplementation",
      "testPaidBarSpecialReleaseImplementation",
      "testPaidBarDebugImplementation",
      "testBasicOtherBarImplementation",
      "testBasicOtherBarReleaseImplementation",
      "testBasicOtherBarSpecialReleaseImplementation",
      "testBasicOtherBarDebugImplementation",
      "testPaidOtherBarImplementation",
      "testPaidOtherBarReleaseImplementation",
      "testPaidOtherBarSpecialReleaseImplementation",
      "testPaidOtherBarDebugImplementation",
      "androidTestImplementation",
      "androidTestBasicImplementation",
      "androidTestPaidImplementation",
      "androidTestBarImplementation",
      "androidTestBasicBarImplementation",
      "androidTestPaidBarImplementation",
      "androidTestOtherBarImplementation",
      "androidTestBasicOtherBarImplementation",
      "androidTestPaidOtherBarImplementation",
    
      "api",
      "releaseApi",
      "specialReleaseApi",
      "debugApi",
      "basicApi",
      "basicReleaseApi",
      "basicSpecialReleaseApi",
      "basicDebugApi",
      "paidApi",
      "paidReleaseApi",
      "paidSpecialReleaseApi",
      "paidDebugApi",
      "barApi",
      "barReleaseApi",
      "barSpecialReleaseApi",
      "barDebugApi",
      "otherBarApi",
      "otherBarReleaseApi",
      "otherBarSpecialReleaseApi",
      "otherBarDebugApi",
      "basicBarApi",
      "basicBarReleaseApi",
      "basicBarSpecialReleaseApi",
      "basicBarDebugApi",
      "paidBarApi",
      "paidBarReleaseApi",
      "paidBarSpecialReleaseApi",
      "paidBarDebugApi",
      "basicOtherBarApi",
      "basicOtherBarReleaseApi",
      "basicOtherBarSpecialReleaseApi",
      "basicOtherBarDebugApi",
      "paidOtherBarApi",
      "paidOtherBarReleaseApi",
      "paidOtherBarSpecialReleaseApi",
      "paidOtherBarDebugApi",
      "testApi",
      "testReleaseApi",
      "testSpecialReleaseApi",
      "testDebugApi",
      "testBasicApi",
      "testBasicReleaseApi",
      "testBasicSpecialReleaseApi",
      "testBasicDebugApi",
      "testPaidApi",
      "testPaidReleaseApi",
      "testPaidSpecialReleaseApi",
      "testPaidDebugApi",
      "testBarApi",
      "testBarReleaseApi",
      "testBarSpecialReleaseApi",
      "testBarDebugApi",
      "testOtherBarApi",
      "testOtherBarReleaseApi",
      "testOtherBarSpecialReleaseApi",
      "testOtherBarDebugApi",
      "testBasicBarApi",
      "testBasicBarReleaseApi",
      "testBasicBarSpecialReleaseApi",
      "testBasicBarDebugApi",
      "testPaidBarApi",
      "testPaidBarReleaseApi",
      "testPaidBarSpecialReleaseApi",
      "testPaidBarDebugApi",
      "testBasicOtherBarApi",
      "testBasicOtherBarReleaseApi",
      "testBasicOtherBarSpecialReleaseApi",
      "testBasicOtherBarDebugApi",
      "testPaidOtherBarApi",
      "testPaidOtherBarReleaseApi",
      "testPaidOtherBarSpecialReleaseApi",
      "testPaidOtherBarDebugApi",
      "androidTestApi",
      "androidTestBasicApi",
      "androidTestPaidApi",
      "androidTestBarApi",
      "androidTestBasicBarApi",
      "androidTestPaidBarApi",
      "androidTestOtherBarApi",
      "androidTestBasicOtherBarApi",
      "androidTestPaidOtherBarApi",
    
      "compileOnly",
      "releaseCompileOnly",
      "specialReleaseCompileOnly",
      "debugCompileOnly",
      "basicCompileOnly",
      "basicReleaseCompileOnly",
      "basicSpecialReleaseCompileOnly",
      "basicDebugCompileOnly",
      "paidCompileOnly",
      "paidReleaseCompileOnly",
      "paidSpecialReleaseCompileOnly",
      "paidDebugCompileOnly",
      "barCompileOnly",
      "barReleaseCompileOnly",
      "barSpecialReleaseCompileOnly",
      "barDebugCompileOnly",
      "otherBarCompileOnly",
      "otherBarReleaseCompileOnly",
      "otherBarSpecialReleaseCompileOnly",
      "otherBarDebugCompileOnly",
      "basicBarCompileOnly",
      "basicBarReleaseCompileOnly",
      "basicBarSpecialReleaseCompileOnly",
      "basicBarDebugCompileOnly",
      "paidBarCompileOnly",
      "paidBarReleaseCompileOnly",
      "paidBarSpecialReleaseCompileOnly",
      "paidBarDebugCompileOnly",
      "basicOtherBarCompileOnly",
      "basicOtherBarReleaseCompileOnly",
      "basicOtherBarSpecialReleaseCompileOnly",
      "basicOtherBarDebugCompileOnly",
      "paidOtherBarCompileOnly",
      "paidOtherBarReleaseCompileOnly",
      "paidOtherBarSpecialReleaseCompileOnly",
      "paidOtherBarDebugCompileOnly",
      "testCompileOnly",
      "testReleaseCompileOnly",
      "testSpecialReleaseCompileOnly",
      "testDebugCompileOnly",
      "testBasicCompileOnly",
      "testBasicReleaseCompileOnly",
      "testBasicSpecialReleaseCompileOnly",
      "testBasicDebugCompileOnly",
      "testPaidCompileOnly",
      "testPaidReleaseCompileOnly",
      "testPaidSpecialReleaseCompileOnly",
      "testPaidDebugCompileOnly",
      "testBarCompileOnly",
      "testBarReleaseCompileOnly",
      "testBarSpecialReleaseCompileOnly",
      "testBarDebugCompileOnly",
      "testOtherBarCompileOnly",
      "testOtherBarReleaseCompileOnly",
      "testOtherBarSpecialReleaseCompileOnly",
      "testOtherBarDebugCompileOnly",
      "testBasicBarCompileOnly",
      "testBasicBarReleaseCompileOnly",
      "testBasicBarSpecialReleaseCompileOnly",
      "testBasicBarDebugCompileOnly",
      "testPaidBarCompileOnly",
      "testPaidBarReleaseCompileOnly",
      "testPaidBarSpecialReleaseCompileOnly",
      "testPaidBarDebugCompileOnly",
      "testBasicOtherBarCompileOnly",
      "testBasicOtherBarReleaseCompileOnly",
      "testBasicOtherBarSpecialReleaseCompileOnly",
      "testBasicOtherBarDebugCompileOnly",
      "testPaidOtherBarCompileOnly",
      "testPaidOtherBarReleaseCompileOnly",
      "testPaidOtherBarSpecialReleaseCompileOnly",
      "testPaidOtherBarDebugCompileOnly",
      "androidTestCompileOnly",
      "androidTestBasicCompileOnly",
      "androidTestPaidCompileOnly",
      "androidTestBarCompileOnly",
      "androidTestBasicBarCompileOnly",
      "androidTestPaidBarCompileOnly",
      "androidTestOtherBarCompileOnly",
      "androidTestBasicOtherBarCompileOnly",
      "androidTestPaidOtherBarCompileOnly",
    
      "annotationProcessor",
      "releaseAnnotationProcessor",
      "specialReleaseAnnotationProcessor",
      "debugAnnotationProcessor",
      "basicAnnotationProcessor",
      "basicReleaseAnnotationProcessor",
      "basicSpecialReleaseAnnotationProcessor",
      "basicDebugAnnotationProcessor",
      "paidAnnotationProcessor",
      "paidReleaseAnnotationProcessor",
      "paidSpecialReleaseAnnotationProcessor",
      "paidDebugAnnotationProcessor",
      "barAnnotationProcessor",
      "barReleaseAnnotationProcessor",
      "barSpecialReleaseAnnotationProcessor",
      "barDebugAnnotationProcessor",
      "otherBarAnnotationProcessor",
      "otherBarReleaseAnnotationProcessor",
      "otherBarSpecialReleaseAnnotationProcessor",
      "otherBarDebugAnnotationProcessor",
      "basicBarAnnotationProcessor",
      "basicBarReleaseAnnotationProcessor",
      "basicBarSpecialReleaseAnnotationProcessor",
      "basicBarDebugAnnotationProcessor",
      "paidBarAnnotationProcessor",
      "paidBarReleaseAnnotationProcessor",
      "paidBarSpecialReleaseAnnotationProcessor",
      "paidBarDebugAnnotationProcessor",
      "basicOtherBarAnnotationProcessor",
      "basicOtherBarReleaseAnnotationProcessor",
      "basicOtherBarSpecialReleaseAnnotationProcessor",
      "basicOtherBarDebugAnnotationProcessor",
      "paidOtherBarAnnotationProcessor",
      "paidOtherBarReleaseAnnotationProcessor",
      "paidOtherBarSpecialReleaseAnnotationProcessor",
      "paidOtherBarDebugAnnotationProcessor",
      "testAnnotationProcessor",
      "testReleaseAnnotationProcessor",
      "testSpecialReleaseAnnotationProcessor",
      "testDebugAnnotationProcessor",
      "testBasicAnnotationProcessor",
      "testBasicReleaseAnnotationProcessor",
      "testBasicSpecialReleaseAnnotationProcessor",
      "testBasicDebugAnnotationProcessor",
      "testPaidAnnotationProcessor",
      "testPaidReleaseAnnotationProcessor",
      "testPaidSpecialReleaseAnnotationProcessor",
      "testPaidDebugAnnotationProcessor",
      "testBarAnnotationProcessor",
      "testBarReleaseAnnotationProcessor",
      "testBarSpecialReleaseAnnotationProcessor",
      "testBarDebugAnnotationProcessor",
      "testOtherBarAnnotationProcessor",
      "testOtherBarReleaseAnnotationProcessor",
      "testOtherBarSpecialReleaseAnnotationProcessor",
      "testOtherBarDebugAnnotationProcessor",
      "testBasicBarAnnotationProcessor",
      "testBasicBarReleaseAnnotationProcessor",
      "testBasicBarSpecialReleaseAnnotationProcessor",
      "testBasicBarDebugAnnotationProcessor",
      "testPaidBarAnnotationProcessor",
      "testPaidBarReleaseAnnotationProcessor",
      "testPaidBarSpecialReleaseAnnotationProcessor",
      "testPaidBarDebugAnnotationProcessor",
      "testBasicOtherBarAnnotationProcessor",
      "testBasicOtherBarReleaseAnnotationProcessor",
      "testBasicOtherBarSpecialReleaseAnnotationProcessor",
      "testBasicOtherBarDebugAnnotationProcessor",
      "testPaidOtherBarAnnotationProcessor",
      "testPaidOtherBarReleaseAnnotationProcessor",
      "testPaidOtherBarSpecialReleaseAnnotationProcessor",
      "testPaidOtherBarDebugAnnotationProcessor",
      "androidTestAnnotationProcessor",
      "androidTestBasicAnnotationProcessor",
      "androidTestPaidAnnotationProcessor",
      "androidTestBarAnnotationProcessor",
      "androidTestBasicBarAnnotationProcessor",
      "androidTestPaidBarAnnotationProcessor",
      "androidTestOtherBarAnnotationProcessor",
      "androidTestBasicOtherBarAnnotationProcessor",
      "androidTestPaidOtherBarAnnotationProcessor"
    )
  }

  fun testConfigurationRequiresWorkaround() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    assertThat(appModule.getConfigurations().filter { appModule.configurationRequiresWorkaround(it) }).containsExactly(
      "basicReleaseImplementation",
      "basicSpecialReleaseImplementation",
      "basicDebugImplementation",
      "paidReleaseImplementation",
      "paidSpecialReleaseImplementation",
      "paidDebugImplementation",
      "barReleaseImplementation",
      "barSpecialReleaseImplementation",
      "barDebugImplementation",
      "otherBarReleaseImplementation",
      "otherBarSpecialReleaseImplementation",
      "otherBarDebugImplementation",
      "basicBarImplementation",
      "basicBarReleaseImplementation",
      "basicBarSpecialReleaseImplementation",
      "basicBarDebugImplementation",
      "paidBarImplementation",
      "paidBarReleaseImplementation",
      "paidBarSpecialReleaseImplementation",
      "paidBarDebugImplementation",
      "basicOtherBarImplementation",
      "basicOtherBarReleaseImplementation",
      "basicOtherBarSpecialReleaseImplementation",
      "basicOtherBarDebugImplementation",
      "paidOtherBarImplementation",
      "paidOtherBarReleaseImplementation",
      "paidOtherBarSpecialReleaseImplementation",
      "paidOtherBarDebugImplementation",

      "testBasicReleaseImplementation",
      "testBasicSpecialReleaseImplementation",
      "testBasicDebugImplementation",
      "testPaidReleaseImplementation",
      "testPaidSpecialReleaseImplementation",
      "testPaidDebugImplementation",
      "testBarReleaseImplementation",
      "testBarSpecialReleaseImplementation",
      "testBarDebugImplementation",
      "testOtherBarReleaseImplementation",
      "testOtherBarSpecialReleaseImplementation",
      "testOtherBarDebugImplementation",
      "testBasicBarImplementation",
      "testBasicBarReleaseImplementation",
      "testBasicBarSpecialReleaseImplementation",
      "testBasicBarDebugImplementation",
      "testPaidBarImplementation",
      "testPaidBarReleaseImplementation",
      "testPaidBarSpecialReleaseImplementation",
      "testPaidBarDebugImplementation",
      "testBasicOtherBarImplementation",
      "testBasicOtherBarReleaseImplementation",
      "testBasicOtherBarSpecialReleaseImplementation",
      "testBasicOtherBarDebugImplementation",
      "testPaidOtherBarImplementation",
      "testPaidOtherBarReleaseImplementation",
      "testPaidOtherBarSpecialReleaseImplementation",
      "testPaidOtherBarDebugImplementation",

      "androidTestBasicBarImplementation",
      "androidTestPaidBarImplementation",
      "androidTestBasicOtherBarImplementation",
      "androidTestPaidOtherBarImplementation",

      "basicReleaseApi",
      "basicSpecialReleaseApi",
      "basicDebugApi",
      "paidReleaseApi",
      "paidSpecialReleaseApi",
      "paidDebugApi",
      "barReleaseApi",
      "barSpecialReleaseApi",
      "barDebugApi",
      "otherBarReleaseApi",
      "otherBarSpecialReleaseApi",
      "otherBarDebugApi",
      "basicBarApi",
      "basicBarReleaseApi",
      "basicBarSpecialReleaseApi",
      "basicBarDebugApi",
      "paidBarApi",
      "paidBarReleaseApi",
      "paidBarSpecialReleaseApi",
      "paidBarDebugApi",
      "basicOtherBarApi",
      "basicOtherBarReleaseApi",
      "basicOtherBarSpecialReleaseApi",
      "basicOtherBarDebugApi",
      "paidOtherBarApi",
      "paidOtherBarReleaseApi",
      "paidOtherBarSpecialReleaseApi",
      "paidOtherBarDebugApi",
      "testBasicReleaseApi",
      "testBasicSpecialReleaseApi",
      "testBasicDebugApi",
      "testPaidReleaseApi",
      "testPaidSpecialReleaseApi",
      "testPaidDebugApi",
      "testBarReleaseApi",
      "testBarSpecialReleaseApi",
      "testBarDebugApi",
      "testOtherBarReleaseApi",
      "testOtherBarSpecialReleaseApi",
      "testOtherBarDebugApi",
      "testBasicBarApi",
      "testBasicBarReleaseApi",
      "testBasicBarSpecialReleaseApi",
      "testBasicBarDebugApi",
      "testPaidBarApi",
      "testPaidBarReleaseApi",
      "testPaidBarSpecialReleaseApi",
      "testPaidBarDebugApi",
      "testBasicOtherBarApi",
      "testBasicOtherBarReleaseApi",
      "testBasicOtherBarSpecialReleaseApi",
      "testBasicOtherBarDebugApi",
      "testPaidOtherBarApi",
      "testPaidOtherBarReleaseApi",
      "testPaidOtherBarSpecialReleaseApi",
      "testPaidOtherBarDebugApi",
      "androidTestBasicBarApi",
      "androidTestPaidBarApi",
      "androidTestBasicOtherBarApi",
      "androidTestPaidOtherBarApi",

      "basicReleaseCompileOnly",
      "basicSpecialReleaseCompileOnly",
      "basicDebugCompileOnly",
      "paidReleaseCompileOnly",
      "paidSpecialReleaseCompileOnly",
      "paidDebugCompileOnly",
      "barReleaseCompileOnly",
      "barSpecialReleaseCompileOnly",
      "barDebugCompileOnly",
      "otherBarReleaseCompileOnly",
      "otherBarSpecialReleaseCompileOnly",
      "otherBarDebugCompileOnly",
      "basicBarCompileOnly",
      "basicBarReleaseCompileOnly",
      "basicBarSpecialReleaseCompileOnly",
      "basicBarDebugCompileOnly",
      "paidBarCompileOnly",
      "paidBarReleaseCompileOnly",
      "paidBarSpecialReleaseCompileOnly",
      "paidBarDebugCompileOnly",
      "basicOtherBarCompileOnly",
      "basicOtherBarReleaseCompileOnly",
      "basicOtherBarSpecialReleaseCompileOnly",
      "basicOtherBarDebugCompileOnly",
      "paidOtherBarCompileOnly",
      "paidOtherBarReleaseCompileOnly",
      "paidOtherBarSpecialReleaseCompileOnly",
      "paidOtherBarDebugCompileOnly",
      "testBasicReleaseCompileOnly",
      "testBasicSpecialReleaseCompileOnly",
      "testBasicDebugCompileOnly",
      "testPaidReleaseCompileOnly",
      "testPaidSpecialReleaseCompileOnly",
      "testPaidDebugCompileOnly",
      "testBarReleaseCompileOnly",
      "testBarSpecialReleaseCompileOnly",
      "testBarDebugCompileOnly",
      "testOtherBarReleaseCompileOnly",
      "testOtherBarSpecialReleaseCompileOnly",
      "testOtherBarDebugCompileOnly",
      "testBasicBarCompileOnly",
      "testBasicBarReleaseCompileOnly",
      "testBasicBarSpecialReleaseCompileOnly",
      "testBasicBarDebugCompileOnly",
      "testPaidBarCompileOnly",
      "testPaidBarReleaseCompileOnly",
      "testPaidBarSpecialReleaseCompileOnly",
      "testPaidBarDebugCompileOnly",
      "testBasicOtherBarCompileOnly",
      "testBasicOtherBarReleaseCompileOnly",
      "testBasicOtherBarSpecialReleaseCompileOnly",
      "testBasicOtherBarDebugCompileOnly",
      "testPaidOtherBarCompileOnly",
      "testPaidOtherBarReleaseCompileOnly",
      "testPaidOtherBarSpecialReleaseCompileOnly",
      "testPaidOtherBarDebugCompileOnly",
      "androidTestBasicBarCompileOnly",
      "androidTestPaidBarCompileOnly",
      "androidTestBasicOtherBarCompileOnly",
      "androidTestPaidOtherBarCompileOnly",

      "basicReleaseAnnotationProcessor",
      "basicSpecialReleaseAnnotationProcessor",
      "basicDebugAnnotationProcessor",
      "paidReleaseAnnotationProcessor",
      "paidSpecialReleaseAnnotationProcessor",
      "paidDebugAnnotationProcessor",
      "barReleaseAnnotationProcessor",
      "barSpecialReleaseAnnotationProcessor",
      "barDebugAnnotationProcessor",
      "otherBarReleaseAnnotationProcessor",
      "otherBarSpecialReleaseAnnotationProcessor",
      "otherBarDebugAnnotationProcessor",
      "basicBarAnnotationProcessor",
      "basicBarReleaseAnnotationProcessor",
      "basicBarSpecialReleaseAnnotationProcessor",
      "basicBarDebugAnnotationProcessor",
      "paidBarAnnotationProcessor",
      "paidBarReleaseAnnotationProcessor",
      "paidBarSpecialReleaseAnnotationProcessor",
      "paidBarDebugAnnotationProcessor",
      "basicOtherBarAnnotationProcessor",
      "basicOtherBarReleaseAnnotationProcessor",
      "basicOtherBarSpecialReleaseAnnotationProcessor",
      "basicOtherBarDebugAnnotationProcessor",
      "paidOtherBarAnnotationProcessor",
      "paidOtherBarReleaseAnnotationProcessor",
      "paidOtherBarSpecialReleaseAnnotationProcessor",
      "paidOtherBarDebugAnnotationProcessor",
      "testBasicReleaseAnnotationProcessor",
      "testBasicSpecialReleaseAnnotationProcessor",
      "testBasicDebugAnnotationProcessor",
      "testPaidReleaseAnnotationProcessor",
      "testPaidSpecialReleaseAnnotationProcessor",
      "testPaidDebugAnnotationProcessor",
      "testBarReleaseAnnotationProcessor",
      "testBarSpecialReleaseAnnotationProcessor",
      "testBarDebugAnnotationProcessor",
      "testOtherBarReleaseAnnotationProcessor",
      "testOtherBarSpecialReleaseAnnotationProcessor",
      "testOtherBarDebugAnnotationProcessor",
      "testBasicBarAnnotationProcessor",
      "testBasicBarReleaseAnnotationProcessor",
      "testBasicBarSpecialReleaseAnnotationProcessor",
      "testBasicBarDebugAnnotationProcessor",
      "testPaidBarAnnotationProcessor",
      "testPaidBarReleaseAnnotationProcessor",
      "testPaidBarSpecialReleaseAnnotationProcessor",
      "testPaidBarDebugAnnotationProcessor",
      "testBasicOtherBarAnnotationProcessor",
      "testBasicOtherBarReleaseAnnotationProcessor",
      "testBasicOtherBarSpecialReleaseAnnotationProcessor",
      "testBasicOtherBarDebugAnnotationProcessor",
      "testPaidOtherBarAnnotationProcessor",
      "testPaidOtherBarReleaseAnnotationProcessor",
      "testPaidOtherBarSpecialReleaseAnnotationProcessor",
      "testPaidOtherBarDebugAnnotationProcessor",
      "androidTestBasicBarAnnotationProcessor",
      "androidTestPaidBarAnnotationProcessor",
      "androidTestBasicOtherBarAnnotationProcessor",
      "androidTestPaidOtherBarAnnotationProcessor"
    )
  }

  private fun PsProject.testSubscribeToNotifications(disposable: Disposable = testRootDisposable) {
    this.onModuleChanged(disposable) { module -> changedModules.add(module.gradlePath.orEmpty()) }
  }

  private fun PsAndroidModule.testSubscribeToChangeNotifications() {
    buildTypes.onChange(testRootDisposable) { buildTypesChanged++ }
    productFlavors.onChange(testRootDisposable) { productFlavorsChanged++ }
    flavorDimensions.onChange(testRootDisposable) { flavorDimensionsChanged++ }
    signingConfigs.onChange(testRootDisposable) { signingConfigsChanged++ }
    resolvedVariants.onChange(testRootDisposable) { variantsChanged++ }
  }
}

internal fun moduleWithoutSyncedModel(project: PsProject, name: String): PsAndroidModule {
  val moduleWithSyncedModel = project.findModuleByName(name) as PsAndroidModule
  return PsAndroidModule(project, moduleWithSyncedModel.gradlePath).apply {
    init(moduleWithSyncedModel.name, null, null, moduleWithSyncedModel.parsedModel)
  }
}

internal fun moduleWithSyncedModel(project: PsProject, name: String): PsAndroidModule = project.findModuleByName(name) as PsAndroidModule
