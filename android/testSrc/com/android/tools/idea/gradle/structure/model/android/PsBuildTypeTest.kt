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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.*
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.groovy.GroovyLanguage
import java.io.File

class PsBuildTypeTest : AndroidGradleTestCase() {

  private fun doTestDescriptor() {
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    assertThat(buildType.descriptor.testEnumerateProperties(), equalTo(PsBuildType.BuildTypeDescriptors.testEnumerateProperties()))
  }

  fun testDescriptorGroovy() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    doTestDescriptor()
  }

  fun testDescriptorKotlin() {
    loadProject(TestProjectPaths.PSD_SAMPLE_KOTLIN)
    doTestDescriptor()
  }

  private fun doTestProperties() {
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    run {
      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule, notNullValue())

      val buildType = appModule.findBuildType("release")
      assertThat(buildType, notNullValue()); buildType!!

      val applicationIdSuffix = PsBuildType.BuildTypeDescriptors.applicationIdSuffix.bind(buildType).getValue()
      val debuggable = PsBuildType.BuildTypeDescriptors.debuggable.bind(buildType).getValue()
      // TODO(b/70501607): Decide on val embedMicroApp = PsBuildType.BuildTypeDescriptors.embedMicroApp.getValue(buildType)
      val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.bind(buildType).getValue()
      val minifyEnabled = PsBuildType.BuildTypeDescriptors.minifyEnabled.bind(buildType).getValue()
      val multiDexEnabled = PsBuildType.BuildTypeDescriptors.multiDexEnabled.bind(buildType).getValue()
      // TODO(b/70501607): Decide on val pseudoLocalesEnabled = PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled.getValue(buildType)
      val renderscriptDebuggable = PsBuildType.BuildTypeDescriptors.renderscriptDebuggable.bind(buildType).getValue()
      val renderscriptOptimLevel = PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel.bind(buildType).getValue()
      val signingConfig = PsBuildType.BuildTypeDescriptors.signingConfig.bind(buildType).getValue()
      // TODO(b/70501607): Decide on val testCoverageEnabled = PsBuildType.BuildTypeDescriptors.testCoverageEnabled.getValue(buildType)
      val versionNameSuffix = PsBuildType.BuildTypeDescriptors.versionNameSuffix.bind(buildType).getValue()
      val zipAlignEnabled = PsBuildType.BuildTypeDescriptors.zipAlignEnabled.bind(buildType).getValue()
      val matchingFallbacks = PsBuildType.BuildTypeDescriptors.matchingFallbacks.bind(buildType).getEditableValues().map { it.getValue() }
      val proGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).getEditableValues().map { it.getValue() }
      val manifestPlaceholders = PsBuildType.BuildTypeDescriptors.manifestPlaceholders.bind(buildType).getValue()

      assertThat(applicationIdSuffix.resolved.asTestValue(), equalTo("suffix"))
      assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo("suffix"))

      assertThat(debuggable.resolved.asTestValue(), equalTo(false))
      assertThat(debuggable.parsedValue.asTestValue(), equalTo(false))

      assertThat(jniDebuggable.resolved.asTestValue(), equalTo(false))
      assertThat(jniDebuggable.parsedValue.asTestValue(), equalTo(false))

      assertThat(minifyEnabled.resolved.asTestValue(), equalTo(false))
      assertThat(minifyEnabled.parsedValue.asTestValue(), equalTo(false))

      assertThat(multiDexEnabled.resolved.asTestValue(), nullValue())
      assertThat(multiDexEnabled.parsedValue.asTestValue(), nullValue())

      assertThat(renderscriptDebuggable.resolved.asTestValue(), equalTo(false))
      assertThat(renderscriptDebuggable.parsedValue.asTestValue(), nullValue())

      assertThat(renderscriptOptimLevel.resolved.asTestValue(), equalTo(2))
      assertThat(renderscriptOptimLevel.parsedValue.asTestValue(), equalTo(2))

      assertThat(signingConfig.resolved.asTestValue(), nullValue())
      val mySigningConfigDslText =  "signingConfigs.myConfig"
      assertThat(
        signingConfig.parsedValue,
        equalTo<Annotated<ParsedValue<Unit>>>(ParsedValue.Set.Parsed(null, DslText.Reference(mySigningConfigDslText)).annotated()))

      assertThat(versionNameSuffix.resolved.asTestValue(), equalTo("vsuffix"))
      assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo("vsuffix"))

      assertThat(zipAlignEnabled.resolved.asTestValue(), equalTo(true))
      assertThat(zipAlignEnabled.parsedValue.asTestValue(), nullValue())

      assertThat(matchingFallbacks.size, equalTo(0))

      assertThat(proGuardFiles.size, equalTo(3))
      assertThat(proGuardFiles[0].resolved.asTestValue(), nullValue())
      // TODO(b/72052622): assertThat(proGuardFiles[0].parsedValue, instanceOf(ParsedValue.Set.Parsed::class.java))
      // TODO(b/72052622): assertThat(
      //  (proGuardFiles[0].parsedValue as ParsedValue.Set.Parsed<File>).dslText?.mode,
      //  equalTo(DslMode.OTHER_UNPARSED_DSL_TEXT)
      //)
      // TODO(b/72052622): assertThat(
      //  (proGuardFiles[0].parsedValue as ParsedValue.Set.Parsed<File>).dslText?.text,
      //  equalTo("getDefaultProguardFile('proguard-android.txt')")
      //)

      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[1].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(File("proguard-rules.txt")))

      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[2].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(File("proguard-rules2.txt")))

      assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
      assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf()))
    }
    run {
      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule, notNullValue())

      val buildType = appModule.findBuildType("specialRelease")
      assertThat(buildType, notNullValue()); buildType!!
      val matchingFallbacks = PsBuildType.BuildTypeDescriptors.matchingFallbacks.bind(buildType).getEditableValues().map { it.getValue() }

      assertThat(matchingFallbacks.size, equalTo(2))
      assertThat(matchingFallbacks[0].resolved.asTestValue(), nullValue())
      assertThat(matchingFallbacks[0].parsedValue.asTestValue(), equalTo("release"))
      assertThat(matchingFallbacks[1].resolved.asTestValue(), nullValue())
      assertThat(matchingFallbacks[1].parsedValue.asTestValue(), equalTo("debug"))
    }
    run {
      val appModule = project.findModuleByName("lib") as PsAndroidModule
      assertThat(appModule, notNullValue())

      val buildType = appModule.findBuildType("release")
      assertThat(buildType, notNullValue()); buildType!!

      val consumerProGuardFiles = PsBuildType.BuildTypeDescriptors.consumerProGuardFiles.bind(buildType).getValue()
      assertThat(consumerProGuardFiles.parsedValue.asTestValue(), equalTo(listOf(File("other.pro"))))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(consumerProGuardFiles.resolved.asTestValue(), equalTo(listOf()))
    }
  }

  fun testPropertiesGroovy() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    doTestProperties()
  }

  fun testPropertiesKotlin() {
    loadProject(TestProjectPaths.PSD_SAMPLE_KOTLIN)
    doTestProperties()
  }

  private fun doTestDefaultResolvedProperties() {
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("debug")
    assertThat(buildType, notNullValue()); buildType!!
    assertTrue(buildType.isDeclared)

    val applicationIdSuffix = PsBuildType.BuildTypeDescriptors.applicationIdSuffix.bind(buildType).getValue()
    val debuggable = PsBuildType.BuildTypeDescriptors.debuggable.bind(buildType).getValue()
    // TODO(b/70501607): Decide on val embedMicroApp = PsBuildType.BuildTypeDescriptors.embedMicroApp.getValue(buildType)
    val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.bind(buildType).getValue()
    val minifyEnabled = PsBuildType.BuildTypeDescriptors.minifyEnabled.bind(buildType).getValue()
    val multiDexEnabled = PsBuildType.BuildTypeDescriptors.multiDexEnabled.bind(buildType).getValue()
    // TODO(b/70501607): Decide on val pseudoLocalesEnabled = PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled.getValue(buildType)
    val renderscriptDebuggable = PsBuildType.BuildTypeDescriptors.renderscriptDebuggable.bind(buildType).getValue()
    val renderscriptOptimLevel = PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel.bind(buildType).getValue()
    // TODO(b/70501607): Decide on val testCoverageEnabled = PsBuildType.BuildTypeDescriptors.testCoverageEnabled.getValue(buildType)
    val versionNameSuffix = PsBuildType.BuildTypeDescriptors.versionNameSuffix.bind(buildType).getValue()
    val zipAlignEnabled = PsBuildType.BuildTypeDescriptors.zipAlignEnabled.bind(buildType).getValue()
    val manifestPlaceholders = PsBuildType.BuildTypeDescriptors.manifestPlaceholders.bind(buildType).getValue()

    assertThat(applicationIdSuffix.resolved.asTestValue(), nullValue())
    assertThat(applicationIdSuffix.parsedValue.asTestValue(), nullValue())

    assertThat(debuggable.resolved.asTestValue(), equalTo(true))
    assertThat(debuggable.parsedValue.asTestValue(), nullValue())

    assertThat(jniDebuggable.resolved.asTestValue(), equalTo(false))
    assertThat(jniDebuggable.parsedValue.asTestValue(), nullValue())

    assertThat(minifyEnabled.resolved.asTestValue(), equalTo(false))
    assertThat(minifyEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(multiDexEnabled.resolved.asTestValue(), nullValue())
    assertThat(multiDexEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(renderscriptDebuggable.resolved.asTestValue(), equalTo(false))
    assertThat(renderscriptDebuggable.parsedValue.asTestValue(), nullValue())

    assertThat(renderscriptOptimLevel.resolved.asTestValue(), equalTo(3))
    assertThat(renderscriptOptimLevel.parsedValue.asTestValue(), nullValue())

    // TODO(b/79142681) signingConfig resolved value is always null.

    assertThat(versionNameSuffix.resolved.asTestValue(), nullValue())
    assertThat(versionNameSuffix.parsedValue.asTestValue(), nullValue())

    assertThat(zipAlignEnabled.resolved.asTestValue(), equalTo(true))
    assertThat(zipAlignEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
    assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf()))
  }

  fun testDefaultResolvedPropertiesGroovy() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    doTestDefaultResolvedProperties()
  }

  fun testDefaultResolvedPropertiesKotlin() {
    loadProject(TestProjectPaths.PSD_SAMPLE_KOTLIN)
    doTestDefaultResolvedProperties()
  }

  private fun doTestSetProperties() {
    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    buildType.applicationIdSuffix = "new_suffix".asParsed()
    buildType.debuggable = true.asParsed()
    buildType.jniDebuggable = true.asParsed()
    buildType.minifyEnabled = true.asParsed()
    buildType.multiDexEnabled = true.asParsed()
    buildType.multiDexEnabled = false.asParsed()
    buildType.renderscriptDebuggable = true.asParsed()
    buildType.renderscriptOptimLevel = 3.asParsed()
    buildType.versionNameSuffix = "new_vsuffix".asParsed()
    buildType.zipAlignEnabled = false.asParsed()
    PsBuildType.BuildTypeDescriptors.signingConfig.bind(buildType).setParsedValue(ParsedValue.NotSet)
    PsBuildType.BuildTypeDescriptors.matchingFallbacks.bind(buildType).run {
      addItem(0).setParsedValue("debug".asParsed())
    }
    PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).run {
      deleteItem(1)
      val editableProGuardFiles = getEditableValues()
      editableProGuardFiles[1].setParsedValue(File("a.txt").asParsed())
      addItem(2).setParsedValue(File("z.txt").asParsed())
    }

    PsBuildType.BuildTypeDescriptors.manifestPlaceholders.bind(buildType).run {
      addEntry("b").setParsedValue("v".asParsed())
      changeEntryKey("b", "v")
      deleteEntry("v")
    }


    fun verifyValues(buildType: PsBuildType, afterSync: Boolean = false) {
      val applicationIdSuffix = PsBuildType.BuildTypeDescriptors.applicationIdSuffix.bind(buildType).getValue()
      val debuggable = PsBuildType.BuildTypeDescriptors.debuggable.bind(buildType).getValue()
      // TODO(b/70501607): Decide on val embedMicroApp = PsBuildType.BuildTypeDescriptors.embedMicroApp.getValue(buildType)
      val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.bind(buildType).getValue()
      val minifyEnabled = PsBuildType.BuildTypeDescriptors.minifyEnabled.bind(buildType).getValue()
      val multiDexEnabled = PsBuildType.BuildTypeDescriptors.multiDexEnabled.bind(buildType).getValue()
      // TODO(b/70501607): Decide on val pseudoLocalesEnabled = PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled.getValue(buildType)
      val renderscriptDebuggable = PsBuildType.BuildTypeDescriptors.renderscriptDebuggable.bind(buildType).getValue()
      val renderscriptOptimLevel = PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel.bind(buildType).getValue()
      val signingConfig = PsBuildType.BuildTypeDescriptors.signingConfig.bind(buildType).getValue()
      // TODO(b/70501607): Decide on val testCoverageEnabled = PsBuildType.BuildTypeDescriptors.testCoverageEnabled.getValue(buildType)
      val versionNameSuffix = PsBuildType.BuildTypeDescriptors.versionNameSuffix.bind(buildType).getValue()
      val zipAlignEnabled = PsBuildType.BuildTypeDescriptors.zipAlignEnabled.bind(buildType).getValue()
      val matchingFallbacks = PsBuildType.BuildTypeDescriptors.matchingFallbacks.bind(buildType).getEditableValues().map { it.getValue() }
      val proGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).getEditableValues().map { it.getValue() }
      val manifestPlaceholders = PsBuildType.BuildTypeDescriptors.manifestPlaceholders.bind(buildType).getValue()

      assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo("new_suffix"))
      assertThat(debuggable.parsedValue.asTestValue(), equalTo(true))
      assertThat(jniDebuggable.parsedValue.asTestValue(), equalTo(true))
      assertThat(minifyEnabled.parsedValue.asTestValue(), equalTo(true))
      assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(false))
      assertThat(renderscriptDebuggable.parsedValue.asTestValue(), equalTo(true))
      assertThat(renderscriptOptimLevel.parsedValue.asTestValue(), equalTo(3))
      assertThat(signingConfig.parsedValue.asTestValue(), nullValue())
      assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo("new_vsuffix"))
      assertThat(zipAlignEnabled.parsedValue.asTestValue(), equalTo(false))

      assertThat(matchingFallbacks.map { it.resolved.asTestValue() }, equalTo<List<String?>>(listOf(null)))
      assertThat(matchingFallbacks.map { it.parsedValue.asTestValue() }, equalTo<List<String?>>(listOf("debug")))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[0].resolved.asTestValue(), nullValue())
      // TODO(b/142454204): DslText is not language-agnostic
      val myDefaultProguardFilesText = when (appModule.parsedModel?.psiFile?.language) {
        is GroovyLanguage -> "getDefaultProguardFile('proguard-android.txt')"
        is KotlinLanguage -> "getDefaultProguardFile(\"proguard-android.txt\")"
        else -> "***unknown language for defaultProguardFile Dsl text***"
      }
      assertThat(proGuardFiles[0].parsedValue.asUnparsedValue(), equalTo(myDefaultProguardFilesText))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[1].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(File("a.txt")))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[2].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(File("z.txt")))

      // TODO(b/144074581): there is a distinction between emptying a map that is the right-hand side of an assignment, where the map itself
      //  must remain in the Dsl, and emptying the implicit map argument to a Dsl method call, where (probably) the entire method call
      //  should be deleted.  The current implementation of manifestPlaceholders in the Dsl parser/model as an always-present property hides
      //  a possible variation in behaviour, the distinction between a missing property and a property with a value of an empty map.
      assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf()))

      if (afterSync) {
        assertThat(applicationIdSuffix.parsedValue.asTestValue(), equalTo(applicationIdSuffix.resolved.asTestValue()))
        assertThat(debuggable.parsedValue.asTestValue(), equalTo(debuggable.resolved.asTestValue()))
        assertThat(jniDebuggable.parsedValue.asTestValue(), equalTo(jniDebuggable.resolved.asTestValue()))
        assertThat(minifyEnabled.parsedValue.asTestValue(), equalTo(minifyEnabled.resolved.asTestValue()))
        assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(multiDexEnabled.resolved.asTestValue()))
        assertThat(renderscriptDebuggable.parsedValue.asTestValue(), equalTo(renderscriptDebuggable.resolved.asTestValue()))
        assertThat(renderscriptOptimLevel.parsedValue.asTestValue(), equalTo(renderscriptOptimLevel.resolved.asTestValue()))
        // TODO(b/79142681) signingConfig resolved value is always null.
        assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo(versionNameSuffix.resolved.asTestValue()))
        assertThat(zipAlignEnabled.parsedValue.asTestValue(), equalTo(zipAlignEnabled.resolved.asTestValue()))
        // Note: Resolved values of matchingFallbacks property are not available.
        // TODO(b/72814329): assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(proGuardFiles[1].resolved.asTestValue()))
        // TODO(b/72814329): assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(proGuardFiles[2].resolved.asTestValue()))

        assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
      }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("release")!!, afterSync = true)
  }

  fun testSetPropertiesGroovy() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    doTestSetProperties()
  }

  fun testSetPropertiesKotlin() {
    loadProject(TestProjectPaths.PSD_SAMPLE_KOTLIN)
    doTestSetProperties()
  }

  private fun doTestUndeclaredDebugSetProperties() {
    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("debug")
    assertThat(buildType, notNullValue()); buildType!!
    assertThat(buildType.isDeclared, equalTo(true))


    buildType.jniDebuggable = true.asParsed()

    fun verifyValues(buildType: PsBuildType, afterSync: Boolean = false) {
      val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.bind(buildType).getValue()

      assertThat(jniDebuggable.parsedValue.asTestValue(), equalTo(true))

      if (afterSync) {
        assertThat(jniDebuggable.parsedValue.asTestValue(), equalTo(jniDebuggable.resolved.asTestValue()))
       }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("debug")!!, afterSync = true)
  }

  fun testUndeclaredDebugSetPropertiesGroovy() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    doTestUndeclaredDebugSetProperties()
  }

  fun testUndeclaredDebugSetPropertiesKotlin() {
    loadProject(TestProjectPaths.PSD_SAMPLE_KOTLIN)
    doTestUndeclaredDebugSetProperties()
  }

  private fun doTestUndeclaredDebugEditLists() {
    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("debug")
    assertThat(buildType, notNullValue()); buildType!!

    PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).run {
      addItem(0).setParsedValue(File("z.txt").asParsed())
    }


    fun verifyValues(buildType: PsBuildType, afterSync: Boolean = false) {
      val proGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).getEditableValues().map { it.getValue() }

      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[0].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(File("z.txt")))

      if (afterSync) {
        // TODO(b/72814329): assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(proGuardFiles[0].resolved.asTestValue()))
       }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("debug")!!, afterSync = true)
  }

  fun testUndeclaredDebugEditListsGroovy() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    doTestUndeclaredDebugEditLists()
  }

  fun testUndeclaredDebugEditListsKotlin() {
    loadProject(TestProjectPaths.PSD_SAMPLE_KOTLIN)
    doTestUndeclaredDebugEditLists()
  }

  private fun doTestUndeclaredDebugEditMaps() {
    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("debug")
    assertThat(buildType, notNullValue()); buildType!!

    PsBuildType.BuildTypeDescriptors.manifestPlaceholders.bind(buildType).run {
      addEntry("k").setParsedValue("v".asParsed<Any>())
    }


    fun verifyValues(buildType: PsBuildType, afterSync: Boolean = false) {
      val manifestPlaceholders =
        PsBuildType.BuildTypeDescriptors.manifestPlaceholders.bind(buildType).getEditableValues().mapValues { it.value.getValue() }

      assertThat(manifestPlaceholders["k"]?.parsedValue?.asTestValue(), equalTo<Any>("v"))

      if (afterSync) {
        assertThat(manifestPlaceholders["k"]?.parsedValue?.asTestValue(), equalTo(manifestPlaceholders["k"]?.resolved?.asTestValue()))
       }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("debug")!!, afterSync = true)
  }

  fun testUndeclaredDebugEditMapsGroovy() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    doTestUndeclaredDebugEditMaps()
  }

  fun testUndeclaredDebugEditMapsKotlin() {
    loadProject(TestProjectPaths.PSD_SAMPLE_KOTLIN)
    doTestUndeclaredDebugEditMaps()
  }

  private fun doTestInsertingProguardFiles() {
    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).run {
      val editableProGuardFiles = getEditableValues()
      editableProGuardFiles[1].setParsedValue(File("a.txt").asParsed())
      editableProGuardFiles[2].setParsedValue(File("b.txt").asParsed())
      addItem(0).setParsedValue(File("z.txt").asParsed())
    }


    fun verifyValues(buildType: PsBuildType, afterSync: Boolean = false) {
      val proGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).getEditableValues().map { it.getValue() }

      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[0].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(File("z.txt")))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[1].resolved.asTestValue(), nullValue())
      // TODO(b/142454204): DslText is not language-agnostic
      val myDefaultProguardFilesText = when (appModule.parsedModel?.psiFile?.language) {
        is GroovyLanguage -> "getDefaultProguardFile('proguard-android.txt')"
        is KotlinLanguage -> "getDefaultProguardFile(\"proguard-android.txt\")"
        else -> "***unknown language for defaultProguardFile Dsl text***"
      }
      assertThat(proGuardFiles[1].parsedValue.asUnparsedValue(), equalTo(myDefaultProguardFilesText))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[2].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(File("a.txt")))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[3].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[3].parsedValue.asTestValue(), equalTo(File("b.txt")))

      if (afterSync) {
        // TODO(b/72814329): assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(proGuardFiles[1].resolved.asTestValue()))
        // TODO(b/72814329): assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(proGuardFiles[2].resolved.asTestValue()))
        // TODO(b/72814329): assertThat(proGuardFiles[3].parsedValue.asTestValue(), equalTo(proGuardFiles[1].resolved.asTestValue()))
       }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("release")!!, afterSync = true)
  }

  fun testInsertingProguardFilesGroovy() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    doTestInsertingProguardFiles()
  }

  fun testInsertingProguardFilesKotlin() {
    loadProject(TestProjectPaths.PSD_SAMPLE_KOTLIN)
    doTestInsertingProguardFiles()
  }

  /** TODO(b/72853928): Enable this test */
  fun /*test*/SetListReferences() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).setParsedValue(
      ParsedValue.Set.Parsed(
        dslText = DslText.Reference("varProGuardFiles"),
        value = null
      )
    )

    fun verifyValues(buildType: PsBuildType, afterSync: Boolean = false) {
      val proGuardFilesValue = PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).getValue()
      val parsedProGuardFilesValue = proGuardFilesValue.parsedValue.value as? ParsedValue.Set.Parsed
      val proGuardFiles = PsBuildType.BuildTypeDescriptors.proGuardFiles.bind(buildType).getEditableValues().map { it.getValue() }

      assertThat(parsedProGuardFilesValue?.dslText, equalTo<DslText?>(DslText.Reference("varProGuardFiles")))

      assertThat(proGuardFiles.size, equalTo(2))
      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[0].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(File("proguard-rules.txt")))

      // TODO(b/72814329): Resolved values are not yet supported on list properties.
      assertThat(proGuardFiles[1].resolved.asTestValue(), nullValue())
      assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(File("proguard-rules2.txt")))

      if (afterSync) {
        // TODO(b/72814329): assertThat(proGuardFiles[0].parsedValue.asTestValue(), equalTo(proGuardFiles[0].resolved.asTestValue()))
        // TODO(b/72814329): assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(proGuardFiles[1].resolved.asTestValue()))
      }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("release")!!, afterSync = true)
  }
}
