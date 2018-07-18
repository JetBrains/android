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
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.testing.TestProjectPaths.*
import com.google.common.collect.Lists
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tests for [PsAndroidModule].
 */
class PsAndroidModuleTest : DependencyTestCase() {

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

  fun testAddFlavorDimension() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    appModule.addNewFlavorDimension("new")
    // A product flavor is required for successful sync.
    val newInNew = appModule.addNewProductFlavor("new_in_new")
    newInNew.dimension = ParsedValue.Set.Parsed("new", DslText.Literal)
    appModule.applyChanges()

    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    val flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions)
      .containsExactly("foo", "bar", "new").inOrder()
  }

  fun testRemoveFlavorDimension() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    appModule.removeFlavorDimension("bar")
    // A product flavor must be removed for successful sync.
    appModule.removeProductFlavor(appModule.findProductFlavor("bar")!!)
    appModule.removeProductFlavor(appModule.findProductFlavor("otherBar")!!)
    var flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions).containsExactly("foo", "bar").inOrder()
    appModule.applyChanges()

    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    flavorDimensions = getFlavorDimensions(appModule)
    assertThat(flavorDimensions).containsExactly("foo")
  }

  private fun getFlavorDimensions(module: PsAndroidModule): List<String> {
    return Lists.newArrayList(module.flavorDimensions)
  }

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

    val basic = appModule.findProductFlavor("basic")
    assertNotNull(basic)
    assertTrue(basic!!.isDeclared)

    val release = appModule.findProductFlavor("paid")
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
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

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
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

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
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

    var productFlavors = appModule.productFlavors
    assertThat(productFlavors.map { it.name })
      .containsExactly("basic", "paid", "bar", "otherBar").inOrder()

    appModule.removeProductFlavor(appModule.findProductFlavor("paid")!!)

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

  fun testAddBuildType() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

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
    assertNotNull(appModule)

    var buildTypes = appModule.buildTypes
    assertThat(buildTypes.map { it.name })
      .containsExactly("release", "specialRelease", "debug").inOrder()

    appModule.removeBuildType(appModule.findBuildType("release")!!)

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

  fun testVariants() {
    loadProject(PROJECT_WITH_APPAND_LIB)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "app")
    assertNotNull(appModule)

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

  fun testAddSigningConfig() {
    loadProject(BASIC)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
    assertNotNull(appModule); appModule!!

    var signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "debug").inOrder()

    val myConfig = appModule.addNewSigningConfig("config2")
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

    var signingConfigs = appModule.signingConfigs
    assertThat(signingConfigs.map { it.name }).containsExactly("myConfig", "debug").inOrder()

    appModule.removeSigningConfig(appModule.findSigningConfig("myConfig")!!)
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
    assertThat(appModule.variants).isEmpty()
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
        assertThat(appModule.dependencies.items.map { "${it.joinedConfigurationNames} ${it.name}" }).containsExactly("api appcompat-v7")
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
      "androidTestPaidOtherBarImplementation")
  }
}

private fun moduleWithoutSyncedModel(project: PsProject, name: String): PsAndroidModule {
  val moduleWithSyncedModel = project.findModuleByName(name) as PsAndroidModule
  return PsAndroidModule(project, moduleWithSyncedModel.gradlePath!!).apply {
    init(moduleWithSyncedModel.name, null, moduleWithSyncedModel.parsedModel)
  }
}

private fun moduleWithSyncedModel(project: PsProject, name: String): PsAndroidModule = project.findModuleByName(name) as PsAndroidModule
