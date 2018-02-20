/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android

import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.CMakeModelImpl
import com.google.common.truth.Truth.assertThat
import java.io.File

/**
 * Tests for [AndroidModelImpl].
 */
class AndroidModelTest : GradleFileModelTestCase() {

  private fun runBasicAndroidBlockTest(text : String) {
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    // Make sure adding to the list works.
    android.flavorDimensions().addListValue().setValue("strawberry")

    applyChangesAndReparse(buildModel)
    // Check that we can get the new parsed value
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("flavorDimensions", listOf("abi", "version", "strawberry"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())
  }

  fun testAndroidBlockWithApplicationStatements() {
    val text = """android {
                    buildToolsVersion "23.0.0"
                    compileSdkVersion 23
                    defaultPublishConfig "debug"
                    flavorDimensions "abi", "version"
                    generatePureSplits true
                    publishNonDefault false
                    resourcePrefix "abcd"
                  }""".trimIndent()
    runBasicAndroidBlockTest(text)
  }

  fun testAndroidBlockWithApplicationStatementsWithParentheses() {
    val text = """android {
                    buildToolsVersion("23.0.0")
                    compileSdkVersion(23)
                    defaultPublishConfig("debug")
                    flavorDimensions("abi", "version")
                    generatePureSplits(true)
                    publishNonDefault(false)
                    resourcePrefix("abcd")
                  }""".trimIndent()
    runBasicAndroidBlockTest(text)
  }


  fun testAndroidBlockWithAssignmentStatements() {
    val text = """android {
                    buildToolsVersion = "23.0.0"
                    compileSdkVersion = "android-23"
                    defaultPublishConfig = "debug"
                    generatePureSplits = true
                  }""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
  }


  fun testAndroidApplicationStatements() {
    val text = """android.buildToolsVersion "23.0.0"
                  android.compileSdkVersion 23
                  android.defaultPublishConfig "debug"
                  android.flavorDimensions "abi", "version"
                  android.generatePureSplits true
                  android.publishNonDefault false
                  android.resourcePrefix "abcd"""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())
  }


  fun testAndroidAssignmentStatements() {
    val text = """android.buildToolsVersion = "23.0.0"
                  android.compileSdkVersion = "android-23"
                  android.defaultPublishConfig = "debug"
                  android.generatePureSplits = true""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
  }


  fun testAndroidBlockWithOverrideStatements() {
    val text = """android {
                    buildToolsVersion = "23.0.0"
                    compileSdkVersion 23
                    defaultPublishConfig "debug"
                    flavorDimensions "abi", "version"
                    generatePureSplits = true
                    publishNonDefault false
                    resourcePrefix "abcd"
                  }
                  android.buildToolsVersion "21.0.0"
                  android.compileSdkVersion = "android-21"
                  android.defaultPublishConfig "release"
                  android.flavorDimensions "abi1", "version1"
                  android.generatePureSplits = false
                  android.publishNonDefault true
                  android.resourcePrefix "efgh"""".trimIndent()


    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "21.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "android-21", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("flavorDimensions", listOf("abi1", "version1"), android.flavorDimensions())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())
  }


  fun testAndroidBlockWithDefaultConfigBlock() {
    val text = """android {
                    defaultConfig {
                      applicationId "com.example.myapplication"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
  }


  fun testAndroidBlockWithBuildTypeBlocks() {
    val text = """android {
                    buildTypes {
                      type1 {
                        applicationIdSuffix "typeSuffix-1"
                      }
                      type2 {
                        applicationIdSuffix "typeSuffix-2"
                      }
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val buildTypes = android!!.buildTypes()
    assertThat(buildTypes).hasSize(2)
    val buildType1 = buildTypes[0]
    assertEquals("name", "type1", buildType1.name())
    assertEquals("applicationIdSuffix", "typeSuffix-1", buildType1.applicationIdSuffix())
    val buildType2 = buildTypes[1]
    assertEquals("name", "type2", buildType2.name())
    assertEquals("applicationIdSuffix", "typeSuffix-2", buildType2.applicationIdSuffix())
  }


  fun testAndroidBlockWithProductFlavorBlocks() {
    val text = """android {
                    productFlavors {
                      flavor1 {
                        applicationId "com.example.myapplication.flavor1"
                      }
                      flavor2 {
                        applicationId "com.example.myapplication.flavor2"
                      }
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val productFlavors = android!!.productFlavors()
    assertThat(productFlavors).hasSize(2)
    val flavor1 = productFlavors[0]
    assertEquals("name", "flavor1", flavor1.name())
    assertEquals("applicationId", "com.example.myapplication.flavor1", flavor1.applicationId())
    val flavor2 = productFlavors[1]
    assertEquals("name", "flavor2", flavor2.name())
    assertEquals("applicationId", "com.example.myapplication.flavor2", flavor2.applicationId())
  }


  fun testAndroidBlockWithExternalNativeBuildBlock() {
    val text = """android {
                    externalNativeBuild {
                      cmake {
                        path file("foo/bar")
                      }
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val externalNativeBuild = android!!.externalNativeBuild()
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl::class.java)
    val cmake = externalNativeBuild.cmake()
    checkForValidPsiElement(cmake, CMakeModelImpl::class.java)
    assertEquals("path", File("foo/bar"), cmake.path())
  }


  fun testRemoveAndResetElements() {
    val text = """android {
                    buildToolsVersion "23.0.0"
                    compileSdkVersion "23"
                    defaultPublishConfig "debug"
                    flavorDimensions "abi", "version"
                    generatePureSplits true
                    publishNonDefault false
                    resourcePrefix "abcd"
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    android.buildToolsVersion().delete()
    android.compileSdkVersion().delete()
    android.defaultPublishConfig().delete()
    android.flavorDimensions().delete()
    android.generatePureSplits().delete()
    android.publishNonDefault().delete()
    android.resourcePrefix().delete()

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("flavorDimensions", android.flavorDimensions())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    buildModel.resetState()

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())
  }


  fun testEditAndResetLiteralElements() {
    val text = """android {
                    buildToolsVersion "23.0.0"
                    compileSdkVersion "23"
                    defaultPublishConfig "debug"
                    generatePureSplits true
                    publishNonDefault false
                    resourcePrefix "abcd"
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    android.buildToolsVersion().setValue("24.0.0")
    android.compileSdkVersion().setValue("24")
    android.defaultPublishConfig().setValue("release")
    android.generatePureSplits().setValue(false)
    android.publishNonDefault().setValue(true)
    android.resourcePrefix().setValue("efgh")

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    buildModel.resetState()

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    // Test the fields that also accept an integer value along with the String valye.
    android.buildToolsVersion().setValue(22)
    android.compileSdkVersion().setValue(21)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    buildModel.resetState()

    assertEquals("buildToolsVersion", "23.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
  }


  fun testAddAndResetLiteralElements() {
    val text = """android {
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("buildToolsVersion", android!!.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    android.buildToolsVersion().setValue("24.0.0")
    android.compileSdkVersion().setValue("24")
    android.defaultPublishConfig().setValue("release")
    android.generatePureSplits().setValue(false)
    android.publishNonDefault().setValue(true)
    android.resourcePrefix().setValue("efgh")

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    buildModel.resetState()

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    // Test the fields that also accept an integer value along with the String value.
    android.buildToolsVersion().setValue(22)
    android.compileSdkVersion().setValue(21)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    buildModel.resetState()

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
  }


  fun testReplaceAndResetListElements() {
    val text = """android {
                    flavorDimensions "abi", "version"
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("flavorDimensions", listOf("abi", "version"), android!!.flavorDimensions())

    android.flavorDimensions().getListValue("abi")!!.setValue("xyz")
    assertEquals("flavorDimensions", listOf("xyz", "version"), android.flavorDimensions())

    buildModel.resetState()
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
  }


  fun testAddAndResetListElements() {
    val text = """android {
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("flavorDimensions", android!!.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("xyz")
    assertEquals("flavorDimensions", listOf("xyz"), android.flavorDimensions())

    buildModel.resetState()
    assertMissingProperty("flavorDimensions", android.flavorDimensions())
  }


  fun testAddToAndResetListElementsWithArgument() {
    val text = """android {
                    flavorDimensions "abi"
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("flavorDimensions", listOf("abi"), android!!.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("version")
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    buildModel.resetState()
    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())
  }


  fun testAddToAndResetListElementsWithMultipleArguments() {
    val text = """android {
                    flavorDimensions "abi", "version"
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("flavorDimensions", listOf("abi", "version"), android!!.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("xyz")
    assertEquals("flavorDimensions", listOf("abi", "version", "xyz"), android.flavorDimensions())

    buildModel.resetState()
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
  }


  fun testRemoveFromAndResetListElements() {
    val text = """android {
                    flavorDimensions "abi", "version"
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertEquals("flavorDimensions", listOf("abi", "version"), android!!.flavorDimensions())

    android.flavorDimensions().getListValue("version")!!.delete()
    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())

    buildModel.resetState()
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
  }


  fun testAddAndResetDefaultConfigBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    checkForInValidPsiElement(android!!.defaultConfig(), ProductFlavorModelImpl::class.java)
    assertMissingProperty(android.defaultConfig().applicationId())

    android.defaultConfig().applicationId().setValue("foo.bar")
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())

    buildModel.resetState()
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)
    assertMissingProperty(android.defaultConfig().applicationId())
  }


  fun testAddAndResetBuildTypeBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertThat(android!!.buildTypes()).isEmpty()

    android.addBuildType("type")
    val buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type", buildTypes[0].name())

    buildModel.resetState()
    assertThat(android.buildTypes()).isEmpty()
  }


  fun testAddAndResetProductFlavorBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    assertThat(android!!.productFlavors()).isEmpty()

    android.addProductFlavor("flavor")
    val productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor", productFlavors[0].name())

    buildModel.resetState()
    assertThat(android.productFlavors()).isEmpty()
  }


  fun testRemoveAndResetBuildTypeBlock() {
    val text = """android {
                    buildTypes {
                      type1 {
                      }
                      type2 {
                      }
                    }
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    var buildTypes = android!!.buildTypes()
    assertThat(buildTypes).hasSize(2)
    assertEquals("buildTypes", "type1", buildTypes[0].name())
    assertEquals("buildTypes", "type2", buildTypes[1].name())

    android.removeBuildType("type1")
    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type2", buildTypes[0].name())

    buildModel.resetState()
    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(2)
    assertEquals("buildTypes", "type1", buildTypes[0].name())
    assertEquals("buildTypes", "type2", buildTypes[1].name())
  }


  fun testRemoveAndResetProductFlavorBlock() {
    val text = """android {
                    productFlavors {
                      flavor1 {
                      }
                      flavor2 {    }
                    }
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    var productFlavors = android!!.productFlavors()
    assertThat(productFlavors).hasSize(2)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())
    assertEquals("productFlavors", "flavor2", productFlavors[1].name())

    android.removeProductFlavor("flavor2")
    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())

    buildModel.resetState()
    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(2)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())
    assertEquals("productFlavors", "flavor2", productFlavors[1].name())
  }


  fun testRemoveAndApplyElements() {
    val text = """android {
                    buildToolsVersion "23.0.0"
                    compileSdkVersion "23"
                    defaultPublishConfig "debug"
                    flavorDimensions "abi", "version"
                    generatePureSplits true
                    publishNonDefault false
                    resourcePrefix "abcd"
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())

    android.buildToolsVersion().delete()
    android.compileSdkVersion().delete()
    android.defaultPublishConfig().delete()
    android.flavorDimensions().delete()
    android.generatePureSplits().delete()
    android.publishNonDefault().delete()
    android.resourcePrefix().delete()

    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("flavorDimensions", android.flavorDimensions())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    applyChanges(buildModel)
    assertMissingProperty("buildToolsVersion", android.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("flavorDimensions", android.flavorDimensions())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    checkForInValidPsiElement(android!!, AndroidModelImpl::class.java)
  }


  fun testAddAndRemoveBuildTypeBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android!!.addBuildType("type")
    val buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    buildTypes[0].applicationIdSuffix().setValue("suffix")
    assertEquals("buildTypes", "type", buildTypes[0].name())

    applyChangesAndReparse(buildModel)
    assertThat(loadBuildFile()).contains("buildTypes")

    android = buildModel.android()
    assertThat(android!!.buildTypes()).hasSize(1)
    android.removeBuildType("type")
    assertThat(android.buildTypes()).isEmpty()

    applyChangesAndReparse(buildModel)
    assertThat(loadBuildFile()).doesNotContain("buildTypes")
  }


  fun testAddAndRemoveProductFlavorBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android!!.addProductFlavor("flavor")
    val productFlavors = android.productFlavors()
    productFlavors[0].applicationId().setValue("appId")
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor", productFlavors[0].name())

    applyChangesAndReparse(buildModel)
    assertThat(loadBuildFile()).contains("productFlavors")

    android = buildModel.android()
    assertThat(android!!.productFlavors()).hasSize(1)
    android.removeProductFlavor("flavor")
    assertThat(android.productFlavors()).isEmpty()

    applyChangesAndReparse(buildModel)
    assertThat(loadBuildFile()).doesNotContain("productFlavors")
  }


  fun testAddAndApplyEmptySigningConfigBlock() {
    val text = """android {
                        }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android!!.addSigningConfig("config")
    val signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertEquals("signingConfigs", "config", signingConfigs[0].name())

    applyChanges(buildModel)
    assertThat(android.signingConfigs()).isEmpty() // Empty blocks are not saved to the file.

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertThat(android!!.signingConfigs()).isEmpty() // Empty blocks are not saved to the file.
  }


  fun testAddAndApplyEmptySourceSetBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android!!.addSourceSet("set")
    val sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set", sourceSets[0].name())

    applyChanges(buildModel)
    assertThat(android.sourceSets()).isEmpty() // Empty blocks are not saved to the file.

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertThat(android!!.sourceSets()).isEmpty() // Empty blocks are not saved to the file.
  }


  fun testAddAndApplyDefaultConfigBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android!!.defaultConfig().applicationId().setValue("foo.bar")
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())

    applyChanges(buildModel)
    assertEquals("defaultConfig", "foo.bar", android.defaultConfig().applicationId())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("defaultConfig", "foo.bar", android!!.defaultConfig().applicationId())
  }


  fun testAddAndApplyBuildTypeBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android!!.addBuildType("type")
    var buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    var buildType = buildTypes[0]
    buildType.applicationIdSuffix().setValue("mySuffix")

    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    buildType = buildTypes[0]
    assertEquals("buildTypes", "type", buildType.name())
    assertEquals("buildTypes", "mySuffix", buildType.applicationIdSuffix())

    applyChanges(buildModel)
    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    buildType = buildTypes[0]
    assertEquals("buildTypes", "type", buildType.name())
    assertEquals("buildTypes", "mySuffix", buildType.applicationIdSuffix())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    buildTypes = android!!.buildTypes()
    assertThat(buildTypes).hasSize(1)
    buildType = buildTypes[0]
    assertEquals("buildTypes", "type", buildType.name())
    assertEquals("buildTypes", "mySuffix", buildType.applicationIdSuffix())
  }


  fun testAddAndApplyProductFlavorBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android!!.addProductFlavor("flavor")
    var productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    var productFlavor = productFlavors[0]
    productFlavor.applicationId().setValue("abc.xyz")

    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    productFlavor = productFlavors[0]
    assertEquals("productFlavors", "flavor", productFlavor.name())
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId())

    applyChanges(buildModel)
    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    productFlavor = productFlavors[0]
    assertEquals("productFlavors", "flavor", productFlavor.name())
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    productFlavors = android!!.productFlavors()
    assertThat(productFlavors).hasSize(1)
    productFlavor = productFlavors[0]
    assertEquals("productFlavors", "flavor", productFlavor.name())
    assertEquals("productFlavors", "abc.xyz", productFlavor.applicationId())
  }


  fun testAddAndApplySigningConfigBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android!!.addSigningConfig("config")
    var signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    var signingConfig = signingConfigs[0]
    signingConfig.keyAlias().setValue("myKeyAlias")

    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    signingConfig = signingConfigs[0]
    assertEquals("signingConfigs", "config", signingConfig.name())
    assertEquals("signingConfigs", "myKeyAlias", signingConfig.keyAlias())

    applyChanges(buildModel)
    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    signingConfig = signingConfigs[0]
    assertEquals("signingConfigs", "config", signingConfig.name())
    assertEquals("signingConfigs", "myKeyAlias", signingConfig.keyAlias())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    signingConfigs = android!!.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    signingConfig = signingConfigs[0]
    assertEquals("signingConfigs", "config", signingConfig.name())
    assertEquals("signingConfigs", "myKeyAlias", signingConfig.keyAlias())
  }


  fun testAddAndApplySourceSetBlock() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    android!!.addSourceSet("set")
    var sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    var sourceSet = sourceSets[0]
    sourceSet.setRoot("source")

    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    assertEquals("sourceSets", "set", sourceSet.name())
    assertEquals("sourceSets", "source", sourceSet.root())

    applyChanges(buildModel)
    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    assertEquals("sourceSets", "set", sourceSet.name())
    assertEquals("sourceSets", "source", sourceSet.root())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    sourceSets = android!!.sourceSets()
    assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    assertEquals("sourceSets", "set", sourceSet.name())
    assertEquals("sourceSets", "source", sourceSet.root())
  }


  fun testRemoveAndApplyDefaultConfigBlock() {
    val text = """android {
                    defaultConfig {
                      applicationId "foo.bar"
                    }
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("defaultConfig", "foo.bar", android!!.defaultConfig().applicationId())
    checkForValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)

    android.defaultConfig().applicationId().delete()
    assertMissingProperty(android.defaultConfig().applicationId())
    checkForValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)

    applyChanges(buildModel)
    assertMissingProperty(android.defaultConfig().applicationId())
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty(android!!.defaultConfig().applicationId())
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)
  }


  fun testRemoveAndApplyBuildTypeBlock() {
    val text = """android {
                            buildTypes {
                              type1 {
                              }
                              type2 {
                              }
                            }
                          }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var buildTypes = android!!.buildTypes()
    assertThat(buildTypes).hasSize(2)
    assertEquals("buildTypes", "type1", buildTypes[0].name())
    assertEquals("buildTypes", "type2", buildTypes[1].name())

    android.removeBuildType("type1")
    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type2", buildTypes[0].name())

    applyChanges(buildModel)
    buildTypes = android.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type2", buildTypes[0].name())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    buildTypes = android!!.buildTypes()
    assertThat(buildTypes).hasSize(1)
    assertEquals("buildTypes", "type2", buildTypes[0].name())
  }


  fun testRemoveAndApplyProductFlavorBlock() {
    val text = """android {
                    productFlavors {
                      flavor1 {
                      }
                      flavor2 {    }
                    }
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var productFlavors = android!!.productFlavors()
    assertThat(productFlavors).hasSize(2)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())
    assertEquals("productFlavors", "flavor2", productFlavors[1].name())

    android.removeProductFlavor("flavor2")
    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())

    applyChanges(buildModel)
    productFlavors = android.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    productFlavors = android!!.productFlavors()
    assertThat(productFlavors).hasSize(1)
    assertEquals("productFlavors", "flavor1", productFlavors[0].name())
  }


  fun testRemoveAndApplySigningConfigBlock() {
    val text = """android {
                    signingConfigs {
                      config1 {
                      }
                      config2 {    }
                    }
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var signingConfigs = android!!.signingConfigs()
    assertThat(signingConfigs).hasSize(2)
    assertEquals("signingConfigs", "config1", signingConfigs[0].name())
    assertEquals("signingConfigs", "config2", signingConfigs[1].name())

    android.removeSigningConfig("config2")
    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertEquals("signingConfigs", "config1", signingConfigs[0].name())

    applyChanges(buildModel)
    signingConfigs = android.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertEquals("signingConfigs", "config1", signingConfigs[0].name())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    signingConfigs = android!!.signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertEquals("signingConfigs", "config1", signingConfigs[0].name())
  }


  fun testRemoveAndApplySourceSetBlock() {
    val text = """android {
                    sourceSets {
                      set1 {
                      }
                      set2 {    }
                    }
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var sourceSets = android!!.sourceSets()
    assertThat(sourceSets).hasSize(2)
    assertEquals("sourceSets", "set1", sourceSets[0].name())
    assertEquals("sourceSets", "set2", sourceSets[1].name())

    android.removeSourceSet("set2")
    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set1", sourceSets[0].name())

    applyChanges(buildModel)
    sourceSets = android.sourceSets()
    assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set1", sourceSets[0].name())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    sourceSets = android!!.sourceSets()
    assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set1", sourceSets[0].name())
  }


  fun testRemoveAndApplyBlockApplicationStatements() {
    val text = """android.defaultConfig.applicationId "com.example.myapplication"
                  android.defaultConfig.proguardFiles "proguard-android.txt", "proguard-rules.pro"""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())

    defaultConfig.applicationId().delete()
    defaultConfig.proguardFiles().delete()

    applyChangesAndReparse(buildModel)
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertMissingProperty(defaultConfig.applicationId())
    assertMissingProperty(defaultConfig.proguardFiles())
  }


  fun testAddAndApplyBlockStatements() {
    val text = """android.defaultConfig.applicationId "com.example.myapplication"
                  android.defaultConfig.proguardFiles "proguard-android.txt", "proguard-rules.pro"""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())

    defaultConfig.dimension().setValue("abcd")
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())

    applyChangesAndReparse(buildModel)
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
  }


  fun testEditAndApplyLiteralElements() {
    val text = """android {
                    buildToolsVersion "23.0.0"
                    compileSdkVersion "23"
                    defaultPublishConfig "debug"
                    generatePureSplits true
                    publishNonDefault false
                    resourcePrefix "abcd"
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "debug", android.defaultPublishConfig())
    assertEquals("generatePureSplits", true, android.generatePureSplits())
    assertEquals("publishNonDefault", false, android.publishNonDefault())
    assertEquals("resourcePrefix", "abcd", android.resourcePrefix())


    android.buildToolsVersion().setValue("24.0.0")
    android.compileSdkVersion().setValue("24")
    android.defaultPublishConfig().setValue("release")
    android.generatePureSplits().setValue(false)
    android.publishNonDefault().setValue(true)
    android.resourcePrefix().setValue("efgh")

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    applyChanges(buildModel)

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "24.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())
  }


  fun testEditAndApplyIntegerLiteralElements() {
    val text = """android {
                    buildToolsVersion "23.0.0"
                    compileSdkVersion "23"
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "23.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "23", android.compileSdkVersion())

    android.buildToolsVersion().setValue(22)
    android.compileSdkVersion().setValue(21)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    applyChanges(buildModel)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "22", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())
  }


  fun testAddAndApplyLiteralElements() {
    val text = """android {
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("buildToolsVersion", android!!.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())
    assertMissingProperty("defaultPublishConfig", android.defaultPublishConfig())
    assertMissingProperty("generatePureSplits", android.generatePureSplits())
    assertMissingProperty("publishNonDefault", android.publishNonDefault())
    assertMissingProperty("resourcePrefix", android.resourcePrefix())

    android.buildToolsVersion().setValue("24.0.0")
    android.compileSdkVersion().setValue("24")
    android.defaultPublishConfig().setValue("release")
    android.generatePureSplits().setValue(false)
    android.publishNonDefault().setValue(true)
    android.resourcePrefix().setValue("efgh")

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    applyChanges(buildModel)

    assertEquals("buildToolsVersion", "24.0.0", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "24.0.0", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "24", android.compileSdkVersion())
    assertEquals("defaultPublishConfig", "release", android.defaultPublishConfig())
    assertEquals("generatePureSplits", false, android.generatePureSplits())
    assertEquals("publishNonDefault", true, android.publishNonDefault())
    assertEquals("resourcePrefix", "efgh", android.resourcePrefix())
  }


  fun testAddAndApplyIntegerLiteralElements() {
    val text = """android {
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("buildToolsVersion", android!!.buildToolsVersion())
    assertMissingProperty("compileSdkVersion", android.compileSdkVersion())

    android.buildToolsVersion().setValue(22)
    android.compileSdkVersion().setValue(21)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    applyChanges(buildModel)

    assertEquals("buildToolsVersion", "22", android.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertEquals("buildToolsVersion", "22", android!!.buildToolsVersion())
    assertEquals("compileSdkVersion", "21", android.compileSdkVersion())
  }


  fun testReplaceAndApplyListElements() {
    val text = """android {
                    flavorDimensions "abi", "version"
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("abi", "version"), android!!.flavorDimensions())

    android.flavorDimensions().getListValue("abi")!!.setValue("xyz")
    assertEquals("flavorDimensions", listOf("xyz", "version"), android.flavorDimensions())

    applyChanges(buildModel)
    assertEquals("flavorDimensions", listOf("xyz", "version"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("xyz", "version"), android!!.flavorDimensions())
  }


  fun testAddAndApplyListElements() {
    val text = """android {
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty("flavorDimensions", android!!.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("xyz")
    assertEquals("flavorDimensions", listOf("xyz"), android.flavorDimensions())

    applyChanges(buildModel)
    assertEquals("flavorDimensions", listOf("xyz"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("xyz"), android!!.flavorDimensions())
  }


  fun testAddToAndApplyListElementsWithOneArgument() {
    val text = """android {
                    flavorDimensions "abi"
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("abi"), android!!.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("version")
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    applyChanges(buildModel)
    assertEquals("flavorDimensions", listOf("abi", "version"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("abi", "version"), android!!.flavorDimensions())
  }


  fun testAddToAndApplyListElementsWithMultipleArguments() {
    val text = """android {
                    flavorDimensions "abi", "version"
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("abi", "version"), android!!.flavorDimensions())

    android.flavorDimensions().addListValue().setValue("xyz")
    assertEquals("flavorDimensions", listOf("abi", "version", "xyz"), android.flavorDimensions())

    applyChanges(buildModel)
    assertEquals("flavorDimensions", listOf("abi", "version", "xyz"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("abi", "version", "xyz"), android!!.flavorDimensions())
  }


  fun testRemoveFromAndApplyListElements() {
    val text = """android {
                    flavorDimensions "abi", "version"
                  }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("abi", "version"), android!!.flavorDimensions())

    android.flavorDimensions().getListValue("version")!!.delete()
    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())

    applyChanges(buildModel)
    assertEquals("flavorDimensions", listOf("abi"), android.flavorDimensions())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    assertEquals("flavorDimensions", listOf("abi"), android!!.flavorDimensions())
  }
}
