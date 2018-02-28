/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License atA
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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.MAP_TYPE
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.ExternalNativeBuildOptionsModelImpl
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.CMakeOptionsModelImpl
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.NdkBuildOptionsModelImpl
import com.google.common.truth.Truth.assertThat


private const val NATIVE_ELEMENTS_TEXT = "android {\n" +
    "  defaultConfig {\n" +
    "    externalNativeBuild {\n" +
    "      cmake {\n" +
    "        abiFilters 'abiFilter1', 'abiFilter2'\n" +
    "        arguments 'argument1', 'argument2'\n" +
    "        cFlags 'cFlag1', 'cFlag2'\n" +
    "        cppFlags 'cppFlag1', 'cppFlag2'\n" +
    "        targets 'target1', 'target2'\n" +
    "      }\n" +
    "      ndkBuild {\n" +
    "        abiFilters 'abiFilter3', 'abiFilter4'\n" +
    "        arguments 'argument3', 'argument4'\n" +
    "        cFlags 'cFlag3', 'cFlag4'\n" +
    "        cppFlags 'cppFlag3', 'cppFlag4'\n" +
    "        targets 'target3', 'target4'\n" +
    "      }\n" +
    "    }\n" +
    "    ndk {\n" +
    "      abiFilter 'abiFilter5'\n" +
    "      abiFilters 'abiFilter6', 'abiFilter7'\n" +
    "    }\n" +
    "  }\n" +
    "}"

/**
 * Tests for [ProductFlavorModelImpl].
 *
 *
 * Both `android.defaultConfig {}` and `android.productFlavors.xyz {}` uses the same structure with same attributes.
 * In this test, the product flavor structure defined by [ProductFlavorModelImpl] is tested in great deal to cover all combinations using
 * the `android.defaultConfig {}` block. The general structure of `android.productFlavors {}` is tested in
 * [ProductFlavorModelTest].
 */
class ProductFlavorModelTest : GradleFileModelTestCase() {
  fun testDefaultConfigBlockWithApplicationStatements() {
    val text = """android {
                    defaultConfig {
                      applicationId "com.example.myapplication"
                      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      dimension "abcd"
                      manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
                      maxSdkVersion 23
                      minSdkVersion 15
                      multiDexEnabled true
                      multiDexKeepFile file('multidex.keep')
                      multiDexKeepProguard file('multidex.proguard')
                      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      renderscriptTargetApi 18
                      renderscriptSupportModeEnabled true
                      renderscriptSupportModeBlasEnabled false
                      renderscriptNdkModeEnabled true
                      resConfigs "abcd", "efgh"
                      resValue "abcd", "efgh", "ijkl"
                      targetSdkVersion 22
                      testApplicationId "com.example.myapplication.test"
                      testFunctionalTest true
                      testHandleProfiling true
                      testInstrumentationRunner "abcd"
                      testInstrumentationRunnerArguments size:"medium", foo:"bar"
                      useJack true
                      vectorDrawables {
                        useSupportLibrary true
                      }
                      versionCode 1
                      versionName "1.0"
                      wearAppUnbundled true
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("multiDexKeepFile", "multidex.keep", defaultConfig.multiDexKeepFile())
    assertEquals("multiDexKeepProguard", "multidex.proguard", defaultConfig.multiDexKeepProguard())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("renderscriptTargetApi", 18, defaultConfig.renderscriptTargetApi())
    assertEquals("renderscriptSupportModeEnabled", true, defaultConfig.renderscriptSupportModeEnabled())
    assertEquals("renderscriptSupportModeBlasEnabled", false, defaultConfig.renderscriptSupportModelBlasEnabled())
    assertEquals("renderscriptNdkModeEnabled", true, defaultConfig.renderscriptNdkModeEnabled())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", true, defaultConfig.useJack())
    val vectorDrawables = defaultConfig.vectorDrawables()
    assertEquals("useSupportLibrary", true, vectorDrawables.useSupportLibrary())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
    assertEquals("wearAppUnbundled", true, defaultConfig.wearAppUnbundled())
  }

  fun testDefaultConfigBlockWithAssignmentStatements() {
    val text = """android.defaultConfig {
                    applicationId = "com.example.myapplication"
                    consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
                    dimension = "abcd"
                    manifestPlaceholders = [activityLabel1:"defaultName1", activityLabel2:"defaultName2"]
                    maxSdkVersion = 23
                    multiDexEnabled = true
                    proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
                    renderscriptTargetApi = 18
                    renderscriptSupportModeEnabled = true
                    renderscriptSupportModeBlasEnabled = false
                    renderscriptNdkModeEnabled = true
                    testApplicationId = "com.example.myapplication.test"
                    testFunctionalTest = true
                    testHandleProfiling = true
                    testInstrumentationRunner = "abcd"
                    testInstrumentationRunnerArguments = [size:"medium", foo:"bar"]
                    useJack = true
                    vectorDrawables {
                        generatedDensities = ['yes', 'no', 'maybe']
                        useSupportLibrary = true
                    }
                    versionCode = 1
                    versionName = "1.0"
                    wearAppUnbundled = true
                  }""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("renderscriptTargetApi", 18, defaultConfig.renderscriptTargetApi())
    assertEquals("renderscriptSupportModeEnabled", true, defaultConfig.renderscriptSupportModeEnabled())
    assertEquals("renderscriptSupportModeBlasEnabled", false, defaultConfig.renderscriptSupportModelBlasEnabled())
    assertEquals("renderscriptNdkModeEnabled", true, defaultConfig.renderscriptNdkModeEnabled())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", true, defaultConfig.useJack())
    val vectorDrawables = defaultConfig.vectorDrawables()
    verifyListProperty(vectorDrawables.generatedDensities(), listOf("yes", "no", "maybe"), true)
    assertEquals("useSupportLibrary", true, vectorDrawables.useSupportLibrary())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
    assertEquals("wearAppUnbundled", true, defaultConfig.wearAppUnbundled())
  }

  fun testDefaultConfigApplicationStatements() {
    val text = """android.defaultConfig.applicationId "com.example.myapplication"
                  android.defaultConfig.consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                  android.defaultConfig.dimension "abcd"
                  android.defaultConfig.manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
                  android.defaultConfig.maxSdkVersion 23
                  android.defaultConfig.minSdkVersion 15
                  android.defaultConfig.multiDexEnabled true
                  android.defaultConfig.proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                  android.defaultConfig.resConfigs "abcd", "efgh"
                  android.defaultConfig.resValue "abcd", "efgh", "ijkl"
                  android.defaultConfig.targetSdkVersion 22
                  android.defaultConfig.testApplicationId "com.example.myapplication.test"
                  android.defaultConfig.testFunctionalTest true
                  android.defaultConfig.testHandleProfiling true
                  android.defaultConfig.testInstrumentationRunner "abcd"
                  android.defaultConfig.testInstrumentationRunnerArguments size:"medium", foo:"bar"
                  android.defaultConfig.useJack true
                  android.defaultConfig.versionCode 1
                  android.defaultConfig.versionName "1.0"""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
  }

  fun testDefaultConfigAssignmentStatements() {
    val text = """android.defaultConfig.applicationId = "com.example.myapplication"
                  android.defaultConfig.consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
                  android.defaultConfig.dimension = "abcd"
                  android.defaultConfig.manifestPlaceholders = [activityLabel1:"defaultName1", activityLabel2:"defaultName2"]
                  android.defaultConfig.maxSdkVersion = 23
                  android.defaultConfig.multiDexEnabled = true
                  android.defaultConfig.proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
                  android.defaultConfig.testApplicationId = "com.example.myapplication.test"
                  android.defaultConfig.testFunctionalTest = true
                  android.defaultConfig.testHandleProfiling = true
                  android.defaultConfig.testInstrumentationRunner = "abcd"
                  android.defaultConfig.testInstrumentationRunnerArguments = [size:"medium", foo:"bar"]
                  android.defaultConfig.useJack = true
                  android.defaultConfig.versionCode = 1
                  android.defaultConfig.versionName = "1.0"""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
  }

  fun testDefaultConfigBlockWithOverrideStatements() {
    val text = """android {
                    defaultConfig {
                      applicationId "com.example.myapplication"
                      consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro'
                      dimension "abcd"
                      manifestPlaceholders = [activityLabel1:"defaultName1", activityLabel2:"defaultName2"]
                      maxSdkVersion 23
                      minSdkVersion 15
                      multiDexEnabled true
                      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      targetSdkVersion 22
                      testApplicationId "com.example.myapplication.test"
                      testFunctionalTest true
                      testHandleProfiling = false
                      testInstrumentationRunner = "abcd"
                      testInstrumentationRunnerArguments = [size:"medium", foo:"bar"]
                      useJack true
                      versionCode 1
                      versionName "1.0"
                    }
                  }
                  android.defaultConfig {
                    applicationId = "com.example.myapplication1"
                    consumerProguardFiles 'proguard-android-1.txt', 'proguard-rules-1.pro'
                    dimension "efgh"
                    manifestPlaceholders activityLabel3:"defaultName3", activityLabel4:"defaultName4"
                    maxSdkVersion = 24
                    minSdkVersion 16
                    multiDexEnabled false
                    proguardFiles = ['proguard-android-1.txt', 'proguard-rules-1.pro']
                    targetSdkVersion 23
                    testApplicationId = "com.example.myapplication.test1"
                    testFunctionalTest false
                    testHandleProfiling true
                    testInstrumentationRunner = "efgh"
                    testInstrumentationRunnerArguments = [key:"value"]
                    useJack = false
                    versionCode 2
                    versionName = "2.0"
                  }
                  android.defaultConfig.versionName = "3.0"""".trimIndent()

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication1", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules-1.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel3" to "defaultName3", "activityLabel4" to "defaultName4"),
        defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules-1.pro"), defaultConfig.proguardFiles())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test1", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("key" to "value"), defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 2, defaultConfig.versionCode())
    assertEquals("versionName", "3.0", defaultConfig.versionName())
  }

  fun testDefaultConfigBlockWithAppendStatements() {
    val text = """android.defaultConfig {
                    proguardFiles = ['pro-1.txt', 'pro-2.txt']
                    resConfigs "abcd", "efgh"
                    resValue "abcd", "efgh", "ijkl"
                    testInstrumentationRunnerArguments = [key1:"value1", key2:"value2"]
                    testInstrumentationRunnerArgument "key3", "value3"
                  }
                  android {
                    defaultConfig {
                      manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
                      proguardFile 'pro-3.txt'
                      resConfigs "ijkl", "mnop"
                      resValue "mnop", "qrst", "uvwx"
                      testInstrumentationRunnerArguments key4:"value4", key5:"value5"
                    }
                  }
                  android.defaultConfig.manifestPlaceholders.activityLabel3 "defaultName3"
                  android.defaultConfig.manifestPlaceholders.activityLabel4 = "defaultName4"
                  android.defaultConfig.proguardFiles 'pro-4.txt', 'pro-5.txt'
                  android.defaultConfig.resConfig "qrst"
                  android.defaultConfig.testInstrumentationRunnerArguments.key6 "value6"
                  android.defaultConfig.testInstrumentationRunnerArguments.key7 = "value7"""".trimIndent()
    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("manifestPlaceholders", 
        mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2", "activityLabel3" to "defaultName3",
            "activityLabel4" to "defaultName4"), defaultConfig.manifestPlaceholders())
    assertEquals("proguardFiles", listOf("pro-1.txt", "pro-2.txt", "pro-3.txt", "pro-4.txt", "pro-5.txt"),
        defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "ijkl", "mnop", "qrst"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
        defaultConfig.resValues())
    val expected = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3", "key4" to "value4",
        "key5" to "value5", "key6" to "value6" , "key7" to "value7")
    assertEquals("testInstrumentationRunnerArguments", expected, defaultConfig.testInstrumentationRunnerArguments())
  }

  fun testDefaultConfigMapStatements() {
    val text = "android.defaultConfig.manifestPlaceholders.activityLabel1 \"defaultName1\"\n" +
        "android.defaultConfig.manifestPlaceholders.activityLabel2 = \"defaultName2\"\n" +
        "android.defaultConfig.testInstrumentationRunnerArguments.key1 \"value1\"\n" +
        "android.defaultConfig.testInstrumentationRunnerArguments.key2 = \"value2\""

    writeToBuildFile(text)

    val android = gradleBuildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("key1" to "value1", "key2" to "value2"),
        defaultConfig.testInstrumentationRunnerArguments())
  }

  fun testRemoveAndResetElements() {
    val text = """android {
                    defaultConfig {
                      applicationId "com.example.myapplication"
                      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      dimension "abcd"
                      manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
                      maxSdkVersion 23
                      minSdkVersion 15
                      multiDexEnabled true
                      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      resConfigs "abcd", "efgh"
                      resValue "abcd", "efgh", "ijkl"
                      targetSdkVersion 22
                      testApplicationId "com.example.myapplication.test"
                      testFunctionalTest false
                      testHandleProfiling true
                      testInstrumentationRunner "abcd"
                      testInstrumentationRunnerArguments size:"medium", foo:"bar"
                      useJack false
                      versionCode 1
                      versionName "1.0"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    defaultConfig.applicationId().delete()
    defaultConfig.consumerProguardFiles().delete()
    defaultConfig.dimension().delete()
    defaultConfig.manifestPlaceholders().delete()
    defaultConfig.maxSdkVersion().delete()
    defaultConfig.minSdkVersion().delete()
    defaultConfig.multiDexEnabled().delete()
    defaultConfig.proguardFiles().delete()
    defaultConfig.resConfigs().delete()
    defaultConfig.removeAllResValues()
    defaultConfig.targetSdkVersion().delete()
    defaultConfig.testApplicationId().delete()
    defaultConfig.testFunctionalTest().delete()
    defaultConfig.testHandleProfiling().delete()
    defaultConfig.testInstrumentationRunner().delete()
    defaultConfig.testInstrumentationRunnerArguments().delete()
    defaultConfig.useJack().delete()
    defaultConfig.versionCode().delete()
    defaultConfig.versionName().delete()

    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertNull("resValues", defaultConfig.resValues())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    buildModel.resetState()

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())
  }

  fun testEditAndResetLiteralElements() {
    val text = """android {
                    defaultConfig {
                      applicationId "com.example.myapplication"
                      dimension "abcd"
                      maxSdkVersion 23
                      minSdkVersion "15"
                      multiDexEnabled true
                      targetSdkVersion "22"
                      testApplicationId "com.example.myapplication.test"
                      testFunctionalTest false
                      testHandleProfiling true
                      testInstrumentationRunner "abcd"
                      useJack false
                      versionCode 1
                      versionName "1.0"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    defaultConfig.applicationId().setValue("com.example.myapplication-1")
    defaultConfig.dimension().setValue("efgh")
    defaultConfig.maxSdkVersion().setValue(24)
    defaultConfig.minSdkVersion().setValue("16")
    defaultConfig.multiDexEnabled().setValue(false)
    defaultConfig.targetSdkVersion().setValue("23")
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test")
    defaultConfig.testFunctionalTest().setValue(true)
    defaultConfig.testHandleProfiling().setValue(false)
    defaultConfig.testInstrumentationRunner().setValue("efgh")
    defaultConfig.useJack().setValue(true)
    defaultConfig.versionCode().setValue("2")
    defaultConfig.versionName().setValue("2.0")

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    buildModel.resetState()

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    // Test the fields that also accept an integer value along with the String value.
    defaultConfig.minSdkVersion().setValue(16)
    defaultConfig.targetSdkVersion().setValue(23)
    defaultConfig.versionCode().setValue(2)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    buildModel.resetState()

    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
  }


  fun testAddAndResetLiteralElements() {
    val text = """android {
                    defaultConfig {
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    defaultConfig.applicationId().setValue("com.example.myapplication-1")
    defaultConfig.dimension().setValue("efgh")
    defaultConfig.maxSdkVersion().setValue(24)
    defaultConfig.minSdkVersion().setValue("16")
    defaultConfig.multiDexEnabled().setValue(false)
    defaultConfig.targetSdkVersion().setValue("23")
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test")
    defaultConfig.testFunctionalTest().setValue(true)
    defaultConfig.testHandleProfiling().setValue(false)
    defaultConfig.testInstrumentationRunner().setValue("efgh")
    defaultConfig.useJack().setValue(true)
    defaultConfig.versionCode().setValue("2")
    defaultConfig.versionName().setValue("2.0")

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    buildModel.resetState()

    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    // Test the fields that also accept an integer value along with the String valye.
    defaultConfig.minSdkVersion().setValue(16)
    defaultConfig.targetSdkVersion().setValue(23)
    defaultConfig.versionCode().setValue(2)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    buildModel.resetState()

    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
  }

  fun testReplaceAndResetListElements() {
    val text = """android {
                    defaultConfig {
                      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      resConfigs "abcd", "efgh"
                      resValue "abcd", "efgh", "ijkl"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    replaceListValue(defaultConfig.consumerProguardFiles(), "proguard-android.txt", "proguard-android-1.txt")
    replaceListValue(defaultConfig.proguardFiles(), "proguard-android.txt", "proguard-android-1.txt")
    replaceListValue(defaultConfig.resConfigs(), "abcd", "xyz")
    defaultConfig.replaceResValue("abcd", "efgh", "ijkl", "abcd", "mnop", "qrst")

    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("xyz", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "mnop", "qrst")), defaultConfig.resValues())

    buildModel.resetState()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
  }

  fun testAddAndResetListElements() {
    val text = """android {
                    defaultConfig {
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertNull("resValues", defaultConfig.resValues())

    defaultConfig.consumerProguardFiles().addListValue().setValue("proguard-android.txt")

    defaultConfig.proguardFiles().addListValue().setValue("proguard-android.txt")
    defaultConfig.resConfigs().addListValue().setValue("abcd")
    defaultConfig.addResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("mnop", "qrst", "uvwx")), defaultConfig.resValues())

    buildModel.resetState()

    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertNull("resValues", defaultConfig.resValues())
  }

  fun testAddToAndResetListElements() {
    val text = """android {
                    defaultConfig {
                      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      resConfigs "abcd", "efgh"
                      resValue "abcd", "efgh", "ijkl"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    defaultConfig.consumerProguardFiles().addListValue().setValue("proguard-android-1.txt")
    defaultConfig.proguardFiles().addListValue().setValue("proguard-android-1.txt")
    defaultConfig.resConfigs().addListValue().setValue("xyz")
    defaultConfig.addResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
        defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "xyz"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
        defaultConfig.resValues())

    buildModel.resetState()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
  }

  fun testRemoveFromAndResetListElements() {
    val text = """android {
                    defaultConfig {
                      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      resConfigs "abcd", "efgh"
                      resValue "abcd", "efgh", "ijkl"
                      resValue "mnop", "qrst", "uvwx"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
        defaultConfig.resValues())

    removeListValue(defaultConfig.consumerProguardFiles(), "proguard-rules.pro")
    removeListValue(defaultConfig.proguardFiles(), "proguard-rules.pro")
    removeListValue(defaultConfig.resConfigs(), "efgh")
    defaultConfig.removeResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    buildModel.resetState()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
        defaultConfig.resValues())
  }

  fun testSetAndResetMapElements() {
    val text = """android {
                    defaultConfig {
                      manifestPlaceholders key1:"value1", key2:"value2"
                      testInstrumentationRunnerArguments size:"medium", foo:"bar"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)
    val defaultConfig = android!!.defaultConfig()

    assertEquals("manifestPlaceholders", mapOf("key1" to "value1", "key2" to "value2"), defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getMapValue("key1").setValue(12345)
    defaultConfig.manifestPlaceholders().getMapValue("key3").setValue(true)
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("size").setValue("small")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("key").setValue("value")

    assertEquals("manifestPlaceholders", mapOf<String, Any>("key1" to 12345, "key2" to "value2", "key3" to true),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "foo" to "bar", "key" to "value"),
        defaultConfig.testInstrumentationRunnerArguments())

    buildModel.resetState()

    assertEquals("manifestPlaceholders", mapOf("key1" to "value1", "key2" to "value2"), defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
  }

  fun testAddAndResetMapElements() {
    val text = """android {
                    defaultConfig {
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)
    val defaultConfig = android!!.defaultConfig()

    assertMissingProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getMapValue("activityLabel1").setValue("newName1")
    defaultConfig.manifestPlaceholders().getMapValue("activityLabel2").setValue("newName2")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("size").setValue("small")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("key").setValue("value")

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "key" to "value"),
        defaultConfig.testInstrumentationRunnerArguments())

    buildModel.resetState()

    assertMissingProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
  }

  fun testRemoveAndResetMapElements() {
    val text = """android {
                    defaultConfig {
                      manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
                      testInstrumentationRunnerArguments size:"medium", foo:"bar"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)
    val defaultConfig = android!!.defaultConfig()

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getValue(MAP_TYPE)!!["activityLabel1"]!!.delete()
    defaultConfig.testInstrumentationRunnerArguments().getValue(MAP_TYPE)!!["size"]!!.delete()

    assertEquals("manifestPlaceholders", mapOf("activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())

    buildModel.resetState()

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
  }

  fun testRemoveAndApplyElements() {
    val text = """android {
                    defaultConfig {
                      applicationId "com.example.myapplication"
                      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      dimension "abcd"
                      manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
                      maxSdkVersion 23
                      minSdkVersion 15
                      multiDexEnabled true
                      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      resConfigs "abcd", "efgh"
                      resValue "abcd", "efgh", "ijkl"
                      targetSdkVersion 22
                      testApplicationId "com.example.myapplication.test"
                      testFunctionalTest false
                      testHandleProfiling true
                      testInstrumentationRunner "abcd"
                      testInstrumentationRunnerArguments size:"medium", foo:"bar"
                      useJack false
                      versionCode 1
                      versionName "1.0"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    checkForValidPsiElement(android!!, AndroidModelImpl::class.java)

    val defaultConfig = android.defaultConfig()
    checkForValidPsiElement(defaultConfig, ProductFlavorModelImpl::class.java)

    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", 15, defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
    assertEquals("targetSdkVersion", 22, defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    defaultConfig.applicationId().delete()
    defaultConfig.consumerProguardFiles().delete()
    defaultConfig.dimension().delete()
    defaultConfig.dimension().delete()
    defaultConfig.manifestPlaceholders().delete()
    defaultConfig.maxSdkVersion().delete()
    defaultConfig.minSdkVersion().delete()
    defaultConfig.multiDexEnabled().delete()
    defaultConfig.proguardFiles().delete()
    defaultConfig.resConfigs().delete()
    defaultConfig.removeAllResValues()
    defaultConfig.targetSdkVersion().delete()
    defaultConfig.testApplicationId().delete()
    defaultConfig.testFunctionalTest().delete()
    defaultConfig.testHandleProfiling().delete()
    defaultConfig.testInstrumentationRunner().delete()
    defaultConfig.testInstrumentationRunnerArguments().delete()
    defaultConfig.useJack().delete()
    defaultConfig.versionCode().delete()
    defaultConfig.versionName().delete()

    checkForValidPsiElement(android, AndroidModelImpl::class.java)
    checkForValidPsiElement(defaultConfig, ProductFlavorModelImpl::class.java)

    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertNull("resValues", defaultConfig.resValues())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    applyChanges(buildModel)
    checkForInValidPsiElement(android, AndroidModelImpl::class.java)
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertNull("resValues", defaultConfig.resValues())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)
    checkForInValidPsiElement(android!!, AndroidModelImpl::class.java)
    checkForInValidPsiElement(android.defaultConfig(), ProductFlavorModelImpl::class.java)
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertNull("resValues", defaultConfig.resValues())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())
  }

  fun testEditAndApplyLiteralElements() {
    val text = """android {
                    defaultConfig {
                      applicationId "com.example.myapplication"
                      dimension "abcd"
                      maxSdkVersion 23
                      minSdkVersion "15"
                      multiDexEnabled true
                      targetSdkVersion "22"
                      testApplicationId "com.example.myapplication.test"
                      testFunctionalTest false
                      testHandleProfiling true
                      testInstrumentationRunner "abcd"
                      useJack false
                      versionCode 1
                      versionName "1.0"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication", defaultConfig.applicationId())
    assertEquals("dimension", "abcd", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(23), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", true, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", false, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", true, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "abcd", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", false, defaultConfig.useJack())
    assertEquals("versionCode", 1, defaultConfig.versionCode())
    assertEquals("versionName", "1.0", defaultConfig.versionName())

    defaultConfig.applicationId().setValue("com.example.myapplication-1")
    defaultConfig.dimension().setValue("efgh")
    defaultConfig.maxSdkVersion().setValue(24)
    defaultConfig.minSdkVersion().setValue("16")
    defaultConfig.multiDexEnabled().setValue(false)
    defaultConfig.targetSdkVersion().setValue("23")
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test")
    defaultConfig.testFunctionalTest().setValue(true)
    defaultConfig.testHandleProfiling().setValue(false)
    defaultConfig.testInstrumentationRunner().setValue("efgh")
    defaultConfig.useJack().setValue(true)
    defaultConfig.versionCode().setValue("2")
    defaultConfig.versionName().setValue("2.0")

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    applyChanges(buildModel)
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())
  }

  fun testEditAndApplyIntegerLiteralElements() {
    val text = """android {
                    defaultConfig {
                      minSdkVersion "15"
                      targetSdkVersion "22"
                      versionCode 1
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertEquals("minSdkVersion", "15", defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", "22", defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 1, defaultConfig.versionCode())

    defaultConfig.minSdkVersion().setValue(16)
    defaultConfig.targetSdkVersion().setValue(23)
    defaultConfig.versionCode().setValue(2)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    applyChanges(buildModel)
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())
  }

  fun testAddAndApplyLiteralElements() {
    val text = """android {
                    defaultConfig {
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("dimension", defaultConfig.dimension())
    assertMissingProperty("maxSdkVersion", defaultConfig.maxSdkVersion())
    assertMissingProperty("minSdkVersion", defaultConfig.minSdkVersion())
    assertMissingProperty("multiDexEnabled", defaultConfig.multiDexEnabled())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("testApplicationId", defaultConfig.testApplicationId())
    assertMissingProperty("testFunctionalTest", defaultConfig.testFunctionalTest())
    assertMissingProperty("testHandleProfiling", defaultConfig.testHandleProfiling())
    assertMissingProperty("testInstrumentationRunner", defaultConfig.testInstrumentationRunner())
    assertMissingProperty("useJack", defaultConfig.useJack())
    assertMissingProperty("versionCode", defaultConfig.versionCode())
    assertMissingProperty("versionName", defaultConfig.versionName())

    defaultConfig.applicationId().setValue("com.example.myapplication-1")
    defaultConfig.dimension().setValue("efgh")
    defaultConfig.maxSdkVersion().setValue(24)
    defaultConfig.minSdkVersion().setValue("16")
    defaultConfig.multiDexEnabled().setValue(false)
    defaultConfig.targetSdkVersion().setValue("23")
    defaultConfig.testApplicationId().setValue("com.example.myapplication-1.test")
    defaultConfig.testFunctionalTest().setValue(true)
    defaultConfig.testHandleProfiling().setValue(false)
    defaultConfig.testInstrumentationRunner().setValue("efgh")
    defaultConfig.useJack().setValue(true)
    defaultConfig.versionCode().setValue("2")
    defaultConfig.versionName().setValue("2.0")

    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    applyChanges(buildModel)
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("applicationId", "com.example.myapplication-1", defaultConfig.applicationId())
    assertEquals("dimension", "efgh", defaultConfig.dimension())
    assertEquals("maxSdkVersion", Integer.valueOf(24), defaultConfig.maxSdkVersion())
    assertEquals("minSdkVersion", "16", defaultConfig.minSdkVersion())
    assertEquals("multiDexEnabled", false, defaultConfig.multiDexEnabled())
    assertEquals("targetSdkVersion", "23", defaultConfig.targetSdkVersion())
    assertEquals("testApplicationId", "com.example.myapplication-1.test", defaultConfig.testApplicationId())
    assertEquals("testFunctionalTest", true, defaultConfig.testFunctionalTest())
    assertEquals("testHandleProfiling", false, defaultConfig.testHandleProfiling())
    assertEquals("testInstrumentationRunner", "efgh", defaultConfig.testInstrumentationRunner())
    assertEquals("useJack", true, defaultConfig.useJack())
    assertEquals("versionCode", "2", defaultConfig.versionCode())
    assertEquals("versionName", "2.0", defaultConfig.versionName())
  }

  fun testAddAndApplyIntegerLiteralElements() {
    val text = """android {
                    defaultConfig {
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertMissingProperty("applicationId", defaultConfig.applicationId())
    assertMissingProperty("targetSdkVersion", defaultConfig.targetSdkVersion())
    assertMissingProperty("versionCode", defaultConfig.versionCode())

    defaultConfig.minSdkVersion().setValue(16)
    defaultConfig.targetSdkVersion().setValue(23)
    defaultConfig.versionCode().setValue(2)

    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    applyChanges(buildModel)
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("minSdkVersion", 16, defaultConfig.minSdkVersion())
    assertEquals("targetSdkVersion", 23, defaultConfig.targetSdkVersion())
    assertEquals("versionCode", 2, defaultConfig.versionCode())
  }

  fun testReplaceAndApplyListElements() {
    val text = """android {
                    defaultConfig {
                      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      resConfigs "abcd", "efgh"
                      resValue "abcd", "efgh", "ijkl"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    replaceListValue(defaultConfig.consumerProguardFiles(), "proguard-android.txt", "proguard-android-1.txt")
    replaceListValue(defaultConfig.proguardFiles(), "proguard-android.txt", "proguard-android-1.txt")
    replaceListValue(defaultConfig.resConfigs(), "abcd", "xyz")
    defaultConfig.replaceResValue("abcd", "efgh", "ijkl", "abcd", "mnop", "qrst")

    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("xyz", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "mnop", "qrst")), defaultConfig.resValues())

    applyChanges(buildModel)
    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("xyz", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "mnop", "qrst")), defaultConfig.resValues())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android-1.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("xyz", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "mnop", "qrst")), defaultConfig.resValues())
  }

  fun testAddAndApplyListElements() {
    val text = """android {
                      defaultConfig {
                      }
                    }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertMissingProperty("consumerProguardFiles", defaultConfig.consumerProguardFiles())
    assertMissingProperty("proguardFiles", defaultConfig.proguardFiles())
    assertMissingProperty("resConfigs", defaultConfig.resConfigs())
    assertNull("resValues", defaultConfig.resValues())

    defaultConfig.consumerProguardFiles().addListValue().setValue("proguard-android.txt")
    defaultConfig.proguardFiles().addListValue().setValue("proguard-android.txt")
    defaultConfig.proguardFiles().addListValue().setValue("proguard-rules.pro")
    defaultConfig.resConfigs().addListValue().setValue("abcd")
    defaultConfig.addResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("mnop", "qrst", "uvwx")), defaultConfig.resValues())

    applyChanges(buildModel)

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("mnop", "qrst", "uvwx")), defaultConfig.resValues())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("mnop", "qrst", "uvwx")), defaultConfig.resValues())
  }

  fun testAddToAndApplyListElements() {
    val text = """android {
                    defaultConfig {
                      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      resConfigs "abcd", "efgh"
                      resValue "abcd", "efgh", "ijkl"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    defaultConfig.consumerProguardFiles().addListValue().setValue("proguard-android-1.txt")
    defaultConfig.proguardFiles().addListValue().setValue("proguard-android-1.txt")
    defaultConfig.resConfigs().addListValue().setValue("xyz")
    defaultConfig.addResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
        defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "xyz"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
        defaultConfig.resValues())

    applyChanges(buildModel)
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
        defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "xyz"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
        defaultConfig.resValues())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
        defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh", "xyz"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
        defaultConfig.resValues())
  }

  fun testRemoveFromAndApplyListElements() {
    val text = """android {
                    defaultConfig {
                      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'
                      proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
                      resConfigs "abcd", "efgh"
                      resValue "abcd", "efgh", "ijkl"
                      resValue "mnop", "qrst", "uvwx"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"),
        defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt", "proguard-rules.pro"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd", "efgh"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl"), listOf("mnop", "qrst", "uvwx")),
        defaultConfig.resValues())

    removeListValue(defaultConfig.consumerProguardFiles(), "proguard-rules.pro")
    removeListValue(defaultConfig.proguardFiles(), "proguard-rules.pro")
    removeListValue(defaultConfig.resConfigs(), "efgh")
    defaultConfig.removeResValue("mnop", "qrst", "uvwx")

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    applyChanges(buildModel)
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-android.txt"), defaultConfig.proguardFiles())
    assertEquals("resConfigs", listOf("abcd"), defaultConfig.resConfigs())
    verifyFlavorType("resValues", listOf(listOf("abcd", "efgh", "ijkl")), defaultConfig.resValues())
  }

  fun testRemoveFromAndApplyListElementsWithSingleElement() {
    val text = """android {
                    defaultConfig {
                      consumerProguardFiles 'proguard-android.txt'
                      proguardFiles = ['proguard-rules.pro']
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()

    assertEquals("consumerProguardFiles", listOf("proguard-android.txt"), defaultConfig.consumerProguardFiles())
    assertEquals("proguardFiles", listOf("proguard-rules.pro"), defaultConfig.proguardFiles())

    removeListValue(defaultConfig.consumerProguardFiles(), "proguard-android.txt")
    removeListValue(defaultConfig.proguardFiles(), "proguard-rules.pro")

    assertThat(defaultConfig.consumerProguardFiles().getValue(LIST_TYPE)).named("consumerProguardFiles").isEmpty()
    assertThat(defaultConfig.proguardFiles().getValue(LIST_TYPE)).named("proguardFiles").isEmpty()

    applyChanges(buildModel)
    assertThat(defaultConfig.consumerProguardFiles().getValue(LIST_TYPE)).named("consumerProguardFiles").isEmpty()
    assertThat(defaultConfig.proguardFiles().getValue(LIST_TYPE)).named("proguardFiles").isEmpty()

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    assertMissingProperty(android!!.defaultConfig().consumerProguardFiles())
    assertSize(0, android.defaultConfig().proguardFiles().getValue(LIST_TYPE))
  }

  fun testSetAndApplyMapElements() {
    val text = """android {
                      defaultConfig {
                        manifestPlaceholders key1:"value1", key2:"value2"
                        testInstrumentationRunnerArguments size:"medium", foo:"bar"
                      }
                    }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("key1" to "value1", "key2" to "value2"), defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getMapValue("key1").setValue(12345)
    defaultConfig.manifestPlaceholders().getMapValue("key3").setValue(true)
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("size").setValue("small")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("key").setValue("value")

    assertEquals("manifestPlaceholders", mapOf("key1" to 12345, "key2" to "value2", "key3" to true),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "foo" to "bar", "key" to "value"),
        defaultConfig.testInstrumentationRunnerArguments())

    applyChanges(buildModel)
    assertEquals("manifestPlaceholders", mapOf("key1" to 12345, "key2" to "value2", "key3" to true),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "foo" to "bar", "key" to "value"),
        defaultConfig.testInstrumentationRunnerArguments())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("key1" to 12345, "key2" to "value2", "key3" to true),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "foo" to "bar", "key" to "value"),
        defaultConfig.testInstrumentationRunnerArguments())
  }

  fun testAddAndApplyMapElements() {
    val text = """android {
                    defaultConfig {
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    assertMissingProperty("manifestPlaceholders", defaultConfig.manifestPlaceholders())
    assertMissingProperty("testInstrumentationRunnerArguments", defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getMapValue("activityLabel1").setValue("newName1")
    defaultConfig.manifestPlaceholders().getMapValue("activityLabel2").setValue("newName2")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("size").setValue("small")
    defaultConfig.testInstrumentationRunnerArguments().getMapValue("key").setValue("value")

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "key" to "value"),
        defaultConfig.testInstrumentationRunnerArguments())

    applyChanges(buildModel)
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "key" to "value"),
        defaultConfig.testInstrumentationRunnerArguments())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "small", "key" to "value"),
        defaultConfig.testInstrumentationRunnerArguments())
  }

  fun testRemoveAndApplyMapElements() {
    val text = """android {
                    defaultConfig {
                      manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
                      testInstrumentationRunnerArguments size:"medium", foo:"bar"
                    }
                  }""".trimIndent()

    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()

    assertEquals("manifestPlaceholders", mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("size" to "medium", "foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())

    defaultConfig.manifestPlaceholders().getValue(MAP_TYPE)!!["activityLabel1"]!!.delete()
    defaultConfig.testInstrumentationRunnerArguments().getValue(MAP_TYPE)!!["size"]!!.delete()

    assertEquals("manifestPlaceholders", mapOf("activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())

    applyChanges(buildModel)
    assertEquals("manifestPlaceholders", mapOf("activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())

    buildModel.reparse()
    android = buildModel.android()
    assertNotNull(android)

    defaultConfig = android!!.defaultConfig()
    assertEquals("manifestPlaceholders", mapOf("activityLabel2" to "defaultName2"),
        defaultConfig.manifestPlaceholders())
    assertEquals("testInstrumentationRunnerArguments", mapOf("foo" to "bar"),
        defaultConfig.testInstrumentationRunnerArguments())
  }

  fun testParseNativeElements() {
    writeToBuildFile(NATIVE_ELEMENTS_TEXT)
    verifyNativeElements()
  }

  fun testEditNativeElements() {
    writeToBuildFile(NATIVE_ELEMENTS_TEXT)
    verifyNativeElements()

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    var externalNativeBuild = defaultConfig.externalNativeBuild()
    var cmake = externalNativeBuild.cmake()
    cmake
        .replaceAbiFilter("abiFilter2", "abiFilterX")
        .replaceArgument("argument2", "argumentX")
        .replaceCFlag("cFlag2", "cFlagX")
        .replaceCppFlag("cppFlag2", "cppFlagX")
        .replaceTarget("target2", "targetX")

    var ndkBuild = externalNativeBuild.ndkBuild()
    ndkBuild
        .replaceAbiFilter("abiFilter4", "abiFilterY")
        .replaceArgument("argument4", "argumentY")
        .replaceCFlag("cFlag4", "cFlagY")
        .replaceCppFlag("cppFlag4", "cppFlagY")
        .replaceTarget("target4", "targetY")

    var ndk = defaultConfig.ndk()
    ndk.replaceAbiFilter("abiFilter6", "abiFilterZ")

    applyChangesAndReparse(buildModel)
    android = buildModel.android()
    assertNotNull(android)
    defaultConfig = android!!.defaultConfig()

    externalNativeBuild = defaultConfig.externalNativeBuild()
    cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilter1", "abiFilterX"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argument1", "argumentX"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlag1", "cFlagX"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlag1", "cppFlagX"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("target1", "targetX"), cmake.targets())

    ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilter3", "abiFilterY"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argument3", "argumentY"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlag3", "cFlagY"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlag3", "cppFlagY"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("target3", "targetY"), ndkBuild.targets())

    ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilter5", "abiFilterZ", "abiFilter7"), ndk.abiFilters())
  }

  fun testAddNativeElements() {
    val text = """android {
                    defaultConfig {
                    }
                  }""".trimIndent()

    writeToBuildFile(text)
    verifyNullNativeElements()

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    var externalNativeBuild = defaultConfig.externalNativeBuild()
    var cmake = externalNativeBuild.cmake()
    cmake
        .addAbiFilter("abiFilterX")
        .addArgument("argumentX")
        .addCFlag("cFlagX")
        .addCppFlag("cppFlagX")
        .addTarget("targetX")

    var ndkBuild = externalNativeBuild.ndkBuild()
    ndkBuild
        .addAbiFilter("abiFilterY")
        .addArgument("argumentY")
        .addCFlag("cFlagY")
        .addCppFlag("cppFlagY")
        .addTarget("targetY")

    var ndk = defaultConfig.ndk()
    ndk.addAbiFilter("abiFilterZ")

    applyChangesAndReparse(buildModel)
    android = buildModel.android()
    assertNotNull(android)
    defaultConfig = android!!.defaultConfig()

    externalNativeBuild = defaultConfig.externalNativeBuild()
    cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilterX"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argumentX"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlagX"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlagX"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("targetX"), cmake.targets())

    ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilterY"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argumentY"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlagY"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlagY"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("targetY"), ndkBuild.targets())

    ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilterZ"), ndk.abiFilters())
  }

  fun testRemoveNativeElements() {
    writeToBuildFile(NATIVE_ELEMENTS_TEXT)
    verifyNativeElements()

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    val externalNativeBuild = defaultConfig.externalNativeBuild()
    val cmake = externalNativeBuild.cmake()
    cmake
        .removeAllAbiFilters()
        .removeAllArguments()
        .removeAllCFlags()
        .removeAllCppFlags()
        .removeAllTargets()

    val ndkBuild = externalNativeBuild.ndkBuild()
    ndkBuild
        .removeAllAbiFilters()
        .removeAllArguments()
        .removeAllCFlags()
        .removeAllCppFlags()
        .removeAllTargets()

    val ndk = defaultConfig.ndk()
    ndk.removeAllAbiFilters()

    applyChangesAndReparse(buildModel)
    verifyNullNativeElements()
  }

  fun testRemoveOneOfNativeElementsInTheList() {
    writeToBuildFile(NATIVE_ELEMENTS_TEXT)
    verifyNativeElements()

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)

    var defaultConfig = android!!.defaultConfig()
    var externalNativeBuild = defaultConfig.externalNativeBuild()
    var cmake = externalNativeBuild.cmake()
    cmake
        .removeAbiFilter("abiFilter1")
        .removeArgument("argument1")
        .removeCFlag("cFlag1")
        .removeCppFlag("cppFlag1")
        .removeTarget("target1")

    var ndkBuild = externalNativeBuild.ndkBuild()
    ndkBuild
        .removeAbiFilter("abiFilter3")
        .removeArgument("argument3")
        .removeCFlag("cFlag3")
        .removeCppFlag("cppFlag3")
        .removeTarget("target3")

    var ndk = defaultConfig.ndk()
    ndk.removeAbiFilter("abiFilter6")

    applyChangesAndReparse(buildModel)
    android = buildModel.android()
    assertNotNull(android)
    defaultConfig = android!!.defaultConfig()

    externalNativeBuild = defaultConfig.externalNativeBuild()
    cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilter2"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argument2"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlag2"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlag2"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("target2"), cmake.targets())

    ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilter4"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argument4"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlag4"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlag4"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("target4"), ndkBuild.targets())

    ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilter5", "abiFilter7"), ndk.abiFilters())
  }

  fun testRemoveOnlyNativeElementInTheList() {
    val text = """android {
                    defaultConfig {
                      externalNativeBuild {
                        cmake {
                          abiFilters 'abiFilterX'
                          arguments 'argumentX'
                          cFlags 'cFlagX'
                          cppFlags 'cppFlagX'
                          targets 'targetX'
                        }
                        ndkBuild {
                          abiFilters 'abiFilterY'
                          arguments 'argumentY'
                          cFlags 'cFlagY'
                          cppFlags 'cppFlagY'
                          targets 'targetY'
                        }
                      }
                      ndk {
                        abiFilters 'abiFilterZ'
                      }
                    }
                  }""".trimIndent()

    writeToBuildFile(text)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)
    val defaultConfig = android!!.defaultConfig()

    val externalNativeBuild = defaultConfig.externalNativeBuild()
    val cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilterX"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argumentX"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlagX"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlagX"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("targetX"), cmake.targets())

    val ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilterY"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argumentY"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlagY"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlagY"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("targetY"), ndkBuild.targets())

    val ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilterZ"), ndk.abiFilters())

    cmake
        .removeAbiFilter("abiFilterX")
        .removeArgument("argumentX")
        .removeCFlag("cFlagX")
        .removeCppFlag("cppFlagX")
        .removeTarget("targetX")

    ndkBuild
        .removeAbiFilter("abiFilterY")
        .removeArgument("argumentY")
        .removeCFlag("cFlagY")
        .removeCppFlag("cppFlagY")
        .removeTarget("targetY")

    ndk.removeAbiFilter("abiFilterZ")

    applyChangesAndReparse(buildModel)
    verifyNullNativeElements()
  }

  private fun verifyNativeElements() {
    val android = gradleBuildModel.android()
    assertNotNull(android)
    val defaultConfig = android!!.defaultConfig()

    val externalNativeBuild = defaultConfig.externalNativeBuild()
    val cmake = externalNativeBuild.cmake()
    assertEquals("cmake-abiFilters", listOf("abiFilter1", "abiFilter2"), cmake.abiFilters())
    assertEquals("cmake-arguments", listOf("argument1", "argument2"), cmake.arguments())
    assertEquals("cmake-cFlags", listOf("cFlag1", "cFlag2"), cmake.cFlags())
    assertEquals("cmake-cppFlags", listOf("cppFlag1", "cppFlag2"), cmake.cppFlags())
    assertEquals("cmake-targets", listOf("target1", "target2"), cmake.targets())

    val ndkBuild = externalNativeBuild.ndkBuild()
    assertEquals("ndkBuild-abiFilters", listOf("abiFilter3", "abiFilter4"), ndkBuild.abiFilters())
    assertEquals("ndkBuild-arguments", listOf("argument3", "argument4"), ndkBuild.arguments())
    assertEquals("ndkBuild-cFlags", listOf("cFlag3", "cFlag4"), ndkBuild.cFlags())
    assertEquals("ndkBuild-cppFlags", listOf("cppFlag3", "cppFlag4"), ndkBuild.cppFlags())
    assertEquals("ndkBuild-targets", listOf("target3", "target4"), ndkBuild.targets())

    val ndk = defaultConfig.ndk()
    assertEquals("ndk-abiFilters", listOf("abiFilter5", "abiFilter6", "abiFilter7"), ndk.abiFilters())
  }

  private fun verifyNullNativeElements() {
    val android = gradleBuildModel.android()
    assertNotNull(android)
    val defaultConfig = android!!.defaultConfig()

    val externalNativeBuild = defaultConfig.externalNativeBuild()
    val cmake = externalNativeBuild.cmake()
    assertNull("cmake-abiFilters", cmake.abiFilters())
    assertNull("cmake-arguments", cmake.arguments())
    assertNull("cmake-cFlags", cmake.cFlags())
    assertNull("cmake-cppFlags", cmake.cppFlags())
    assertNull("cmake-targets", cmake.targets())
    checkForInValidPsiElement(cmake, CMakeOptionsModelImpl::class.java)

    val ndkBuild = externalNativeBuild.ndkBuild()
    assertNull("ndkBuild-abiFilters", ndkBuild.abiFilters())
    assertNull("ndkBuild-arguments", ndkBuild.arguments())
    assertNull("ndkBuild-cFlags", ndkBuild.cFlags())
    assertNull("ndkBuild-cppFlags", ndkBuild.cppFlags())
    assertNull("ndkBuild-targets", ndkBuild.targets())
    checkForInValidPsiElement(ndkBuild, NdkBuildOptionsModelImpl::class.java)

    val ndk = defaultConfig.ndk()
    assertNull("ndk-abiFilters", ndk.abiFilters())
    checkForInValidPsiElement(ndk, NdkOptionsModelImpl::class.java)
  }

  fun testRemoveNativeBlockElements() {
    val text = """android {
                    defaultConfig {
                      externalNativeBuild {
                      }
                      ndk {
                      }
                    }
                  }""".trimIndent()

    writeToBuildFile(text)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    var defaultConfig = android!!.defaultConfig()
    checkForValidPsiElement(defaultConfig.externalNativeBuild(), ExternalNativeBuildOptionsModelImpl::class.java)
    checkForValidPsiElement(defaultConfig.ndk(), NdkOptionsModelImpl::class.java)

    defaultConfig.removeExternalNativeBuild()
    defaultConfig.removeNdk()

    applyChangesAndReparse(buildModel)
    android = buildModel.android()
    assertNotNull(android)
    defaultConfig = android!!.defaultConfig()
    checkForInValidPsiElement(defaultConfig.externalNativeBuild(), ExternalNativeBuildOptionsModelImpl::class.java)
    checkForInValidPsiElement(defaultConfig.ndk(), NdkOptionsModelImpl::class.java)
  }

  fun testRemoveExternalNativeBlockElements() {
    val text = """android {
                    defaultConfig {
                      externalNativeBuild {
                        cmake {
                        }
                        ndkBuild {
                        }
                      }
                    }
                  }""".trimIndent()

    writeToBuildFile(text)
    val buildModel = gradleBuildModel
    var android = buildModel.android()
    assertNotNull(android)
    var externalNativeBuild = android!!.defaultConfig().externalNativeBuild()
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeOptionsModelImpl::class.java)
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildOptionsModelImpl::class.java)

    externalNativeBuild.removeCMake()
    externalNativeBuild.removeNdkBuild()

    applyChangesAndReparse(buildModel)
    android = buildModel.android()
    assertNotNull(android)
    externalNativeBuild = android!!.defaultConfig().externalNativeBuild()
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeOptionsModelImpl::class.java)
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildOptionsModelImpl::class.java)
  }

  fun testFunctionCallWithParentheses() {
    val text = """android {
                    defaultConfig {
                      applicationId "com.example.psd.sample.app.default"
                      testApplicationId "com.example.psd.sample.app.default.test"
                      maxSdkVersion 26
                      minSdkVersion 9
                      targetSdkVersion(19)
                      versionCode 1
                      versionName "1.0"
                    }
                  }""".trimIndent()
    writeToBuildFile(text)
    val buildModel = gradleBuildModel
    val android = buildModel.android()
    assertNotNull(android)

    val defaultConfig = android!!.defaultConfig()
    assertEquals("targetSdkVersion", 19, defaultConfig.targetSdkVersion())
  }
}
