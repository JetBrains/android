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

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.GradleResolver
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tests for [PsAndroidModule].
 */
@RunsInEdt
class PsAndroidModuleTest {

  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  val changedModules = mutableSetOf<String>()
  var buildTypesChanged = 0
  var productFlavorsChanged = 0
  var flavorDimensionsChanged = 0
  var signingConfigsChanged = 0
  var variantsChanged = 0

  private val DISALLOWED_NAME_CHARS = "/\\:<>\"?*|\$'"

  @Test
  fun testFlavorDimensions() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {

      val appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      val flavorDimensions = getFlavorDimensions(appModule)
      assertThat(flavorDimensions)
        .containsExactly("foo", "bar").inOrder()
    }
  }

  @Test
  fun testFallbackFlavorDimensions() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {

      val appModule = moduleWithoutSyncedModel(project, "app")
      assertNotNull(appModule)

      val flavorDimensions = getFlavorDimensions(appModule)
      assertThat(flavorDimensions)
        .containsExactly("foo", "bar").inOrder()
    }
  }

  @Test
  fun testScriptedFlavorDimensions() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SCRIPTED_DIMENSIONS)
    projectRule.psTestWithProject(preparedProject) {

      run {
        val appModule = moduleWithoutSyncedModel(project, "app")
        assertNotNull(appModule)

        val flavorDimensions = getFlavorDimensions(appModule)
        assertThat(flavorDimensions).isEmpty()
      }

      run {
        val appModule = moduleWithSyncedModel(project, "app")
        assertNotNull(appModule)

        val flavorDimensions = getFlavorDimensions(appModule)
        assertThat(flavorDimensions)
          .containsExactly("dimScripted").inOrder()
      }
    }
  }

  @Test
  fun testValidateFlavorDimensionName() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {

      val appModule = moduleWithoutSyncedModel(project, "app")
      assertNotNull(appModule)

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
  }

  @Test
  fun testAddFlavorDimension() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {

      var appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)
      appModule.testSubscribeToChangeNotifications()

      appModule.addNewFlavorDimension("new")
      assertThat(flavorDimensionsChanged).isEqualTo(1)
      // A product flavor is required for successful sync.
      val newInNew = appModule.addNewProductFlavor("new", "new_in_new")
      appModule.applyChanges()

      requestSyncAndWait()
      reparse()
      appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      val flavorDimensions = getFlavorDimensions(appModule)
      assertThat(flavorDimensions)
        .containsExactly("foo", "bar", "new").inOrder()
    }
  }

  @Test
  fun testAddFirstFlavorDimension() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {

      var libModule = moduleWithSyncedModel(project, "lib")
      assertNotNull(libModule)
      libModule.testSubscribeToChangeNotifications()

      libModule.addNewFlavorDimension("bar")
      assertThat(flavorDimensionsChanged).isEqualTo(1)
      // A product flavor is required for successful sync.
      val bar = libModule.addNewProductFlavor("bar", "bar")
      val otherBar = libModule.addNewProductFlavor("bar", "otherBar")
      libModule.applyChanges()

      requestSyncAndWait()
      reparse()
      libModule = moduleWithSyncedModel(project, "lib")
      assertNotNull(libModule)

      val flavorDimensions = getFlavorDimensions(libModule)
      assertThat(flavorDimensions).containsExactly("bar")
    }
  }

  @Test
  fun testRemoveFlavorDimension() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {

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
      reparse()
      appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      flavorDimensions = getFlavorDimensions(appModule)
      assertThat(flavorDimensions).containsExactly("foo")
    }
  }

  private fun getFlavorDimensions(module: PsAndroidModule): List<String> = module.flavorDimensions.map { it.name }

  @Test
  fun testProductFlavors() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
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
  }

  @Test
  fun testFallbackProductFlavors() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
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
  }

  @Test
  fun testValidateProductFlavorName() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = moduleWithoutSyncedModel(project, "app")
      assertNotNull(appModule)

      assertThat(appModule.validateProductFlavorName("", "foo")).isEqualTo("Product flavor name cannot be empty.")
      assertThat(appModule.validateProductFlavorName("", "bar")).isEqualTo("Product flavor name cannot be empty.")
      assertThat(appModule.validateProductFlavorName("", null)).isEqualTo("Product flavor name cannot be empty.")
      assertThat(appModule.validateProductFlavorName("test", "foo")).isEqualTo("Product flavor name cannot start with 'test'.")
      assertThat(appModule.validateProductFlavorName("test", "bar")).isEqualTo("Product flavor name cannot start with 'test'.")
      assertThat(appModule.validateProductFlavorName("test", null)).isEqualTo("Product flavor name cannot start with 'test'.")
      assertThat(appModule.validateProductFlavorName("testable", "foo")).isEqualTo("Product flavor name cannot start with 'test'.")
      assertThat(appModule.validateProductFlavorName("testable", "bar")).isEqualTo("Product flavor name cannot start with 'test'.")
      assertThat(appModule.validateProductFlavorName("testable", null)).isEqualTo("Product flavor name cannot start with 'test'.")
      assertThat(
        appModule.validateProductFlavorName(
          "androidTest",
          "foo"
        )
      ).isEqualTo("Product flavor name cannot start with 'androidTest'.")
      assertThat(
        appModule.validateProductFlavorName(
          "androidTest",
          "bar"
        )
      ).isEqualTo("Product flavor name cannot start with 'androidTest'.")
      assertThat(appModule.validateProductFlavorName("androidTest", null)).isEqualTo("Product flavor name cannot start with 'androidTest'.")
      assertThat(appModule.validateProductFlavorName("androidTestable", "foo")).isEqualTo(
        "Product flavor name cannot start with 'androidTest'."
      )
      assertThat(appModule.validateProductFlavorName("androidTestable", "bar")).isEqualTo(
        "Product flavor name cannot start with 'androidTest'."
      )
      assertThat(appModule.validateProductFlavorName("androidTestable", null)).isEqualTo(
        "Product flavor name cannot start with 'androidTest'."
      )
      assertThat(appModule.validateProductFlavorName("main", "foo")).isEqualTo("Product flavor name cannot be 'main'.")
      assertThat(appModule.validateProductFlavorName("main", "bar")).isEqualTo("Product flavor name cannot be 'main'.")
      assertThat(appModule.validateProductFlavorName("main", null)).isEqualTo("Product flavor name cannot be 'main'.")
      assertThat(appModule.validateProductFlavorName("lint", "foo")).isEqualTo("Product flavor name cannot be 'lint'.")
      assertThat(appModule.validateProductFlavorName("lint", "bar")).isEqualTo("Product flavor name cannot be 'lint'.")
      assertThat(appModule.validateProductFlavorName("lint", null)).isEqualTo("Product flavor name cannot be 'lint'.")
      assertThat(appModule.validateProductFlavorName("specialRelease", "foo"))
        .isEqualTo("Product flavor name cannot collide with build type: 'specialRelease'")
      assertThat(appModule.validateProductFlavorName("specialRelease", "bar"))
        .isEqualTo("Product flavor name cannot collide with build type: 'specialRelease'")
      assertThat(appModule.validateProductFlavorName("specialRelease", null))
        .isEqualTo("Product flavor name cannot collide with build type: 'specialRelease'")
      assertThat(appModule.validateProductFlavorName("paid", "foo")).isEqualTo("Duplicate product flavor name: 'paid'")
      assertThat(appModule.validateProductFlavorName("paid", "bar")).isEqualTo("Duplicate product flavor name: 'paid'")
      assertThat(appModule.validateProductFlavorName("paid", null)).isEqualTo("Duplicate product flavor name: 'paid'")
      assertThat(appModule.validateProductFlavorName("ok", "foo")).isNull()
      assertThat(appModule.validateProductFlavorName("ok", "bar")).isNull()
      assertThat(appModule.validateProductFlavorName("ok", null)).isNull()
      DISALLOWED_NAME_CHARS.forEach {
        assertThat(appModule.validateProductFlavorName("${it}", "foo")).isNotNull()
        assertThat(appModule.validateProductFlavorName("foo${it}", "foo")).isNotNull()
        assertThat(appModule.validateProductFlavorName("foo${it}bar", "foo")).isNotNull()
        assertThat(appModule.validateProductFlavorName("'${it}'", "foo")).isNotNull()
        assertThat(appModule.validateProductFlavorName("''${it}''", "foo")).isNotNull()
        assertThat(appModule.validateProductFlavorName("${it}", "bar")).isNotNull()
        assertThat(appModule.validateProductFlavorName("foo${it}", "bar")).isNotNull()
        assertThat(appModule.validateProductFlavorName("foo${it}bar", "bar")).isNotNull()
        assertThat(appModule.validateProductFlavorName("'${it}'", "bar")).isNotNull()
        assertThat(appModule.validateProductFlavorName("''${it}''", "bar")).isNotNull()
        assertThat(appModule.validateProductFlavorName("${it}", null)).isNotNull()
        assertThat(appModule.validateProductFlavorName("foo${it}", null)).isNotNull()
        assertThat(appModule.validateProductFlavorName("foo${it}bar", null)).isNotNull()
        assertThat(appModule.validateProductFlavorName("'${it}'", null)).isNotNull()
        assertThat(appModule.validateProductFlavorName("''${it}''", null)).isNotNull()
      }
    }
  }

  @Test
  fun testValidateProductFlavorNameWithCollisions() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VARIANT_COLLISIONS)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = moduleWithoutSyncedModel(project, "app")
      assertNotNull(appModule)
      assertThat(appModule.validateProductFlavorName("paid", "foo")).isEqualTo("Duplicate product flavor name: 'paid'")
      assertThat(appModule.validateProductFlavorName("paidLittle", "foo")).isEqualTo("Duplicate product flavor name: 'paidLittle'")

      // even in the first flavor dimension, the first character will end up capitalized in some configuration names...
      assertThat(appModule.validateProductFlavorName("Paid", "foo"))
        .isEqualTo("Duplicate product flavor name: 'Paid'")
      // ... but characters after the first in names are not case-converted
      assertThat(appModule.validateProductFlavorName("pAid", "foo")).isNull()

      // "paid" product flavor + "specialRelease" build type = "paidSpecial" product flavor + "release" build type
      assertThat(appModule.validateProductFlavorName("paidSpecial", "foo"))
        .isEqualTo("Product flavor name 'paidSpecial' in flavor dimension 'foo' would cause a configuration name ambiguity.")

      val app2Module = moduleWithoutSyncedModel(project, "app2")

      // "paid" product flavor + "littleScreen" product flavor + "specialRelease" build type
      // =
      // "paidLittle" product flavor + "screenSpecial" product flavor + "release" build type
      assertThat(app2Module.validateProductFlavorName("screenSpecial", "bar"))
        .isEqualTo("Product flavor name 'screenSpecial' in flavor dimension 'bar' would cause a configuration name ambiguity.")
      // but this one is OK
      assertThat(app2Module.validateProductFlavorName("screenSpecial", "foo")).isNull()

      val app3Module = moduleWithoutSyncedModel(project, "app3")

      // "paid" product flavor + "little" product flavor = "paidLittle" product flavor (whichever dimension)
      assertThat(app3Module.validateProductFlavorName("paidLittle", "foo"))
        .isEqualTo("Product flavor name 'paidLittle' in flavor dimension 'foo' would cause a configuration name ambiguity.")
      assertThat(app3Module.validateProductFlavorName("paidLittle", "bar"))
        .isEqualTo("Product flavor name 'paidLittle' in flavor dimension 'bar' would cause a configuration name ambiguity.")

      assertThat(app3Module.validateProductFlavorName("PaidLittle", "foo"))
        .isEqualTo("Product flavor name 'PaidLittle' in flavor dimension 'foo' would cause a configuration name ambiguity.")
      assertThat(app3Module.validateProductFlavorName("PaidLittle", "bar"))
        .isEqualTo("Product flavor name 'PaidLittle' in flavor dimension 'bar' would cause a configuration name ambiguity.")
    }
  }

  @Test
  fun testAddProductFlavor() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
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
      reparse()
      appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      productFlavors = appModule.productFlavors
      assertThat(productFlavors.map { it.name })
        .containsExactly("basic", "paid", "new_flavor").inOrder()

      newFlavor = appModule.findProductFlavor("foo", "new_flavor")
      assertNotNull(newFlavor)
      assertNotNull(newFlavor!!.resolvedModel)
    }
  }

  @Test
  fun testRemoveProductFlavor() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
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
      reparse()
      appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      productFlavors = appModule.productFlavors
      assertThat(productFlavors.map { it.name })
        .containsExactly("basic", "bar", "otherBar").inOrder()
    }
  }

  @Test
  fun testRenameProductFlavor() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
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
      reparse()
      appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      productFlavors = appModule.productFlavors
      assertThat(productFlavors.map { it.name })
        .containsExactly("basic", "paidLittle", "bar", "otherBar").inOrder()
    }
  }

  @Test
  fun testBuildTypes() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
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
      assertTrue(debug!!.isDeclared)
    }
  }

  @Test
  fun testFallbackBuildTypes() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val libModule = moduleWithoutSyncedModel(project, "lib")
      assertNotNull(libModule)

      val buildTypes = libModule.buildTypes
      assertThat(buildTypes.map { it.name }).containsExactly("release", "debug")
      assertThat(buildTypes).hasSize(2)

      val release = libModule.findBuildType("release")
      assertNotNull(release)
      assertTrue(release!!.isDeclared)
    }
  }

  @Test
  fun testValidateBuildTypeName() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = moduleWithoutSyncedModel(project, "app")
      assertNotNull(appModule)

      assertThat(appModule.validateBuildTypeName("")).isEqualTo("Build type name cannot be empty.")
      assertThat(appModule.validateBuildTypeName("test")).isEqualTo("Build type name cannot start with 'test'.")
      assertThat(appModule.validateBuildTypeName("testable")).isEqualTo("Build type name cannot start with 'test'.")
      assertThat(appModule.validateBuildTypeName("androidTest")).isEqualTo("Build type name cannot start with 'androidTest'.")
      assertThat(appModule.validateBuildTypeName("androidTestable")).isEqualTo("Build type name cannot start with 'androidTest'.")
      assertThat(appModule.validateBuildTypeName("main")).isEqualTo("Build type name cannot be 'main'.")
      assertThat(appModule.validateBuildTypeName("lint")).isEqualTo("Build type name cannot be 'lint'.")
      assertThat(appModule.validateBuildTypeName("otherBar"))
        .isEqualTo("Build type name cannot collide with product flavor: 'otherBar'")
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
  }

  @Test
  fun testValidateBuildTypeNameWithCollisions() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VARIANT_COLLISIONS)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = moduleWithoutSyncedModel(project, "app")
      assertNotNull(appModule)
      assertThat(appModule.validateBuildTypeName("release")).isEqualTo("Duplicate build type name: 'release'")
      assertThat(appModule.validateBuildTypeName("specialRelease")).isEqualTo("Duplicate build type name: 'specialRelease'")

      // "paid" flavor + "littleRelease" buildType = "paidLittle" flavor + "release" buildType
      assertThat(appModule.validateBuildTypeName("littleRelease"))
        .isEqualTo("Build type name 'littleRelease' would cause a configuration name ambiguity.")

      val app2Module = moduleWithoutSyncedModel(project, "app2")
      assertNotNull(app2Module)
      assertThat(app2Module.validateBuildTypeName("release")).isEqualTo("Duplicate build type name: 'release'")
      assertThat(app2Module.validateBuildTypeName("specialRelease")).isEqualTo("Duplicate build type name: 'specialRelease'")

      // "paid" flavor + "littleScreen" flavor + "release" buildType = "paidLittle" flavor + "screenRelease" buildType
      assertThat(app2Module.validateBuildTypeName("screenRelease"))
        .isEqualTo("Build type name 'screenRelease' would cause a configuration name ambiguity.")

      val app4Module = moduleWithoutSyncedModel(project, "app4")
      assertNotNull(app4Module)
      assertThat(app4Module.validateBuildTypeName("Experimental"))
        .isEqualTo("Duplicate build type name: 'Experimental'")
      assertThat(app4Module.validateBuildTypeName("exPerimental")).isNull()
    }
  }

  @Test
  fun testAddBuildType() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
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
      reparse()
      appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      buildTypes = appModule.buildTypes
      assertThat(buildTypes.map { it.name })
        .containsExactly("release", "debug", "new_build_type").inOrder()

      newBuildType = appModule.findBuildType("new_build_type")
      assertNotNull(newBuildType)
      assertNotNull(newBuildType!!.resolvedModel)
    }
  }

  @Test
  fun testRemoveBuildType() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      var appModule = moduleWithSyncedModel(project, "app")
      appModule.testSubscribeToChangeNotifications()
      assertNotNull(appModule)

      var buildTypes = appModule.buildTypes
      assertThat(buildTypes.map { it.name })
        .containsExactly("release", "debug", "specialRelease").inOrder()

      appModule.removeBuildType(appModule.findBuildType("release")!!)
      assertThat(buildTypesChanged).isEqualTo(1)

      buildTypes = appModule.buildTypes
      assertThat(buildTypes.map { it.name })
        .containsExactly("specialRelease", "debug")

      appModule.applyChanges()
      requestSyncAndWait()
      reparse()
      appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      buildTypes = appModule.buildTypes
      assertThat(buildTypes.map { it.name })
        .containsExactly("release", "debug", "specialRelease").inOrder()

      val release = appModule.findBuildType("release")
      assertNotNull(release)
      assertTrue(release!!.isDeclared)
    }
  }

  @Test
  fun testRenameBuildType() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      var appModule = moduleWithSyncedModel(project, "app")
      appModule.testSubscribeToChangeNotifications()
      assertNotNull(appModule)

      var buildTypes = appModule.buildTypes
      assertThat(buildTypes.map { it.name })
        .containsExactly("release", "debug", "specialRelease").inOrder()

      appModule.findBuildType("release")!!.rename("almostRelease")
      assertThat(buildTypesChanged).isEqualTo(1)

      buildTypes = appModule.buildTypes
      assertThat(buildTypes.map { it.name })
        .containsExactly("almostRelease", "specialRelease", "debug")

      appModule.applyChanges()
      requestSyncAndWait()
      reparse()
      appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      buildTypes = appModule.buildTypes
      assertThat(buildTypes.map { it.name })
        .containsExactly("release", "debug", "almostRelease", "specialRelease").inOrder()

      val release = appModule.findBuildType("release")
      assertNotNull(release)
      assertTrue(release!!.isDeclared)
    }
  }

  @Test
  fun testVariants() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
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
  }

  @Test
  fun testCanDependOnModules() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      val libModule = moduleWithSyncedModel(project, "lib")
      assertNotNull(libModule)

      assertTrue(appModule.canDependOn(libModule))
      assertFalse(libModule.canDependOn(appModule))
    }
  }

  @Test
  fun testSigningConfigs() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BASIC)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(appModule); appModule!!

      val signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs).hasSize(2)

      val myConfig = appModule.findSigningConfig("myConfig")
      assertNotNull(myConfig)
      assertTrue(myConfig!!.isDeclared)

      val debugConfig = appModule.findSigningConfig("debug")
      assertNotNull(debugConfig)
      assertTrue(debugConfig!!.isDeclared)
    }
  }

  @Test
  fun testValidateSigningConfigName() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = moduleWithoutSyncedModel(project, "app")
      assertNotNull(appModule)

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
  }

  @Test
  fun testAddSigningConfig() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BASIC)
    projectRule.psTestWithProject(preparedProject) {
      var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(appModule); appModule!!
      appModule.testSubscribeToChangeNotifications()

      var signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "myConfig").inOrder()

      val myConfig = appModule.addNewSigningConfig("config2")
      assertThat(signingConfigsChanged).isEqualTo(1)
      myConfig.storeFile = ParsedValue.Set.Parsed(File("/tmp/1"), DslText.Literal)

      assertNotNull(myConfig)
      assertTrue(myConfig.isDeclared)

      signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "myConfig", "config2").inOrder()

      appModule.applyChanges()
      requestSyncAndWait()
      reparse()
      appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(appModule); appModule!!

      signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "myConfig", "config2").inOrder()
    }
  }

  @Test
  fun testRemoveSigningConfig() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BASIC)
    projectRule.psTestWithProject(preparedProject) {
      var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(appModule); appModule!!
      appModule.testSubscribeToChangeNotifications()

      var signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "myConfig").inOrder()

      appModule.removeSigningConfig(appModule.findSigningConfig("myConfig")!!)
      assertThat(signingConfigsChanged).isEqualTo(1)
      appModule.removeBuildType(appModule.findBuildType("debug")!!)  // Remove (clean) the build type that refers to the signing config.

      signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug")

      appModule.applyChanges()
      requestSyncAndWait()
      reparse()
      appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(appModule); appModule!!

      signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug")
    }
  }

  @Test
  fun testRenameSigningConfig() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BASIC)
    projectRule.psTestWithProject(preparedProject) {
      var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(appModule); appModule!!
      appModule.testSubscribeToChangeNotifications()

      var signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "myConfig").inOrder()

      appModule.findSigningConfig("myConfig")!!.rename("yourConfig")
      assertThat(signingConfigsChanged).isEqualTo(1)
      appModule.removeBuildType(appModule.findBuildType("debug")!!)  // Remove (clean) the build type that refers to the signing config.

      signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "yourConfig").inOrder()

      appModule.applyChanges()
      requestSyncAndWait()
      reparse()
      appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(appModule); appModule!!

      signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "yourConfig").inOrder()
    }
  }

  @Test
  fun testRenameSigningConfigAndReferences() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BASIC)
    projectRule.psTestWithProject(preparedProject) {
      var appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(appModule); appModule!!
      appModule.testSubscribeToChangeNotifications()

      var signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "myConfig").inOrder()

      appModule.findSigningConfig("myConfig")!!.rename("yourConfig", true)
      assertThat(signingConfigsChanged).isEqualTo(1)

      signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "yourConfig").inOrder()

      appModule.applyChanges()
      requestSyncAndWait()
      reparse()
      appModule = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(appModule); appModule!!

      signingConfigs = appModule.signingConfigs
      assertThat(signingConfigs.map { it.name }).containsExactly("debug", "yourConfig").inOrder()

      val debugSigningConfig = PsBuildType.BuildTypeDescriptors.signingConfig.bind(appModule.findBuildType("debug")!!).getValue()
      assertThat(debugSigningConfig.parsedValue)
        .isEqualTo(ParsedValue.Set.Parsed(null, DslText.Reference("signingConfigs.yourConfig")).annotated())
    }
  }

  @Test
  fun testApplyChangesDropsResolvedValues() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BASIC)
    projectRule.psTestWithProject(preparedProject) {
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
  }

  @Test
  fun testApplyChangesAndSyncReloadsResolvedValues() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
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
            .containsExactly(
              "api appcompat-v7", "api libs",
              "implementation constraint-layout",
              "implementation runner",
              "implementation espresso-core"
            )
        } finally {
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
        } finally {
          Disposer.dispose(disposable)
        }
      }
    }
  }

  @Test
  fun testLoadingRootlessProject() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BASIC)
    projectRule.psTestWithProject(preparedProject) {
      val module = project.findModuleByGradlePath(":") as PsAndroidModule?
      assertNotNull(module); module!!

      assertThat(module.buildTypes.map { it.name }).containsExactly("debug", "release")
      assertThat(module.productFlavors.map { it.name }).isEmpty()
      assertThat(module.signingConfigs.map { it.name }).containsExactly("myConfig", "debug")
      assertThat(module.dependencies.items.map { "${it.joinedConfigurationNames} ${it.name}" })
        .containsExactly("debugApi support-v13", "api support-v4")
    }
  }

  @Test
  fun testResolvingProjectReloadsCollections() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
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
  }

  @Test
  fun testModuleChanged() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
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
  }

  @Test
  fun testImportantConfigurations() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      assertThat(appModule.getConfigurations(onlyImportantFor = PsModule.ImportantFor.LIBRARY)).containsExactly(
        "implementation",
        "releaseImplementation",
        "specialReleaseImplementation",
        "debugImplementation",
        "basicImplementation",
        "paidImplementation",
        "barImplementation",
        "otherBarImplementation",
        "testImplementation",
        "testReleaseImplementation",
        "testSpecialReleaseImplementation",
        "testDebugImplementation",
        "testBasicImplementation",
        "testPaidImplementation",
        "testBarImplementation",
        "testOtherBarImplementation",
        "androidTestImplementation",
        "androidTestBasicImplementation",
        "androidTestPaidImplementation",
        "androidTestBarImplementation",
        "androidTestOtherBarImplementation"
      )

      assertThat(appModule.getConfigurations(onlyImportantFor = PsModule.ImportantFor.MODULE)).containsExactly(
        "implementation",
        "api",
        "testImplementation",
        "testApi",
        "androidTestImplementation",
        "androidTestApi"
      )
    }
  }

  @Test
  fun testConfigurations() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      assertThat(appModule.getConfigurations()).containsExactly(
        "implementation",
        "releaseImplementation",
        "specialReleaseImplementation",
        "debugImplementation",
        "basicImplementation",
        "paidImplementation",
        "barImplementation",
        "otherBarImplementation",
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
        "testPaidImplementation",
        "testBarImplementation",
        "testOtherBarImplementation",
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
        "paidApi",
        "barApi",
        "otherBarApi",
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
        "testPaidApi",
        "testBarApi",
        "testOtherBarApi",
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
        "paidCompileOnly",
        "barCompileOnly",
        "otherBarCompileOnly",
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
        "testPaidCompileOnly",
        "testBarCompileOnly",
        "testOtherBarCompileOnly",
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

        "runtimeOnly",
        "releaseRuntimeOnly",
        "specialReleaseRuntimeOnly",
        "debugRuntimeOnly",
        "basicRuntimeOnly",
        "paidRuntimeOnly",
        "barRuntimeOnly",
        "otherBarRuntimeOnly",
        "basicBarRuntimeOnly",
        "basicBarReleaseRuntimeOnly",
        "basicBarSpecialReleaseRuntimeOnly",
        "basicBarDebugRuntimeOnly",
        "paidBarRuntimeOnly",
        "paidBarReleaseRuntimeOnly",
        "paidBarSpecialReleaseRuntimeOnly",
        "paidBarDebugRuntimeOnly",
        "basicOtherBarRuntimeOnly",
        "basicOtherBarReleaseRuntimeOnly",
        "basicOtherBarSpecialReleaseRuntimeOnly",
        "basicOtherBarDebugRuntimeOnly",
        "paidOtherBarRuntimeOnly",
        "paidOtherBarReleaseRuntimeOnly",
        "paidOtherBarSpecialReleaseRuntimeOnly",
        "paidOtherBarDebugRuntimeOnly",
        "testRuntimeOnly",
        "testReleaseRuntimeOnly",
        "testSpecialReleaseRuntimeOnly",
        "testDebugRuntimeOnly",
        "testBasicRuntimeOnly",
        "testPaidRuntimeOnly",
        "testBarRuntimeOnly",
        "testOtherBarRuntimeOnly",
        "testBasicBarRuntimeOnly",
        "testBasicBarReleaseRuntimeOnly",
        "testBasicBarSpecialReleaseRuntimeOnly",
        "testBasicBarDebugRuntimeOnly",
        "testPaidBarRuntimeOnly",
        "testPaidBarReleaseRuntimeOnly",
        "testPaidBarSpecialReleaseRuntimeOnly",
        "testPaidBarDebugRuntimeOnly",
        "testBasicOtherBarRuntimeOnly",
        "testBasicOtherBarReleaseRuntimeOnly",
        "testBasicOtherBarSpecialReleaseRuntimeOnly",
        "testBasicOtherBarDebugRuntimeOnly",
        "testPaidOtherBarRuntimeOnly",
        "testPaidOtherBarReleaseRuntimeOnly",
        "testPaidOtherBarSpecialReleaseRuntimeOnly",
        "testPaidOtherBarDebugRuntimeOnly",
        "androidTestRuntimeOnly",
        "androidTestBasicRuntimeOnly",
        "androidTestPaidRuntimeOnly",
        "androidTestBarRuntimeOnly",
        "androidTestBasicBarRuntimeOnly",
        "androidTestPaidBarRuntimeOnly",
        "androidTestOtherBarRuntimeOnly",
        "androidTestBasicOtherBarRuntimeOnly",
        "androidTestPaidOtherBarRuntimeOnly",

        "annotationProcessor",
        "releaseAnnotationProcessor",
        "specialReleaseAnnotationProcessor",
        "debugAnnotationProcessor",
        "basicAnnotationProcessor",
        "paidAnnotationProcessor",
        "barAnnotationProcessor",
        "otherBarAnnotationProcessor",
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
        "testPaidAnnotationProcessor",
        "testBarAnnotationProcessor",
        "testOtherBarAnnotationProcessor",
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
  }

  @Test
  fun testConfigurationRequiresWorkaround() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = moduleWithSyncedModel(project, "app")
      assertNotNull(appModule)

      assertThat(appModule.getConfigurations().filter { appModule.configurationRequiresWorkaround(it) }).containsExactly(
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

        "basicBarRuntimeOnly",
        "basicBarReleaseRuntimeOnly",
        "basicBarSpecialReleaseRuntimeOnly",
        "basicBarDebugRuntimeOnly",
        "paidBarRuntimeOnly",
        "paidBarReleaseRuntimeOnly",
        "paidBarSpecialReleaseRuntimeOnly",
        "paidBarDebugRuntimeOnly",
        "basicOtherBarRuntimeOnly",
        "basicOtherBarReleaseRuntimeOnly",
        "basicOtherBarSpecialReleaseRuntimeOnly",
        "basicOtherBarDebugRuntimeOnly",
        "paidOtherBarRuntimeOnly",
        "paidOtherBarReleaseRuntimeOnly",
        "paidOtherBarSpecialReleaseRuntimeOnly",
        "paidOtherBarDebugRuntimeOnly",
        "testBasicBarRuntimeOnly",
        "testBasicBarReleaseRuntimeOnly",
        "testBasicBarSpecialReleaseRuntimeOnly",
        "testBasicBarDebugRuntimeOnly",
        "testPaidBarRuntimeOnly",
        "testPaidBarReleaseRuntimeOnly",
        "testPaidBarSpecialReleaseRuntimeOnly",
        "testPaidBarDebugRuntimeOnly",
        "testBasicOtherBarRuntimeOnly",
        "testBasicOtherBarReleaseRuntimeOnly",
        "testBasicOtherBarSpecialReleaseRuntimeOnly",
        "testBasicOtherBarDebugRuntimeOnly",
        "testPaidOtherBarRuntimeOnly",
        "testPaidOtherBarReleaseRuntimeOnly",
        "testPaidOtherBarSpecialReleaseRuntimeOnly",
        "testPaidOtherBarDebugRuntimeOnly",
        "androidTestBasicBarRuntimeOnly",
        "androidTestPaidBarRuntimeOnly",
        "androidTestBasicOtherBarRuntimeOnly",
        "androidTestPaidOtherBarRuntimeOnly",

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
  }

  private fun PsProject.testSubscribeToNotifications(disposable: Disposable = projectRule.testRootDisposable) {
    this.onModuleChanged(disposable) { module -> changedModules.add(module.gradlePath.orEmpty()) }
  }

  private fun PsAndroidModule.testSubscribeToChangeNotifications() {
    buildTypes.onChange(projectRule.testRootDisposable) { buildTypesChanged++ }
    productFlavors.onChange(projectRule.testRootDisposable) { productFlavorsChanged++ }
    flavorDimensions.onChange(projectRule.testRootDisposable) { flavorDimensionsChanged++ }
    signingConfigs.onChange(projectRule.testRootDisposable) { signingConfigsChanged++ }
    resolvedVariants.onChange(projectRule.testRootDisposable) { variantsChanged++ }
  }
}

internal fun moduleWithoutSyncedModel(project: PsProject, name: String): PsAndroidModule {
  val moduleWithSyncedModel = project.findModuleByName(name) as PsAndroidModule
  return PsAndroidModule(project, moduleWithSyncedModel.gradlePath).apply {
    init(moduleWithSyncedModel.name, null, null, null, null, moduleWithSyncedModel.parsedModel)
  }
}

internal fun moduleWithSyncedModel(project: PsProject, name: String): PsAndroidModule = project.findModuleByName(name) as PsAndroidModule
