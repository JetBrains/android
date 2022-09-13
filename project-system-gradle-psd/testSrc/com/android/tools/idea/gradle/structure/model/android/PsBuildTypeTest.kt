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

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.withoutKtsRelatedIndexing
import com.google.common.truth.Expect
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import junit.framework.Assert.assertTrue
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class PsBuildTypeTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  private fun doTestDescriptor(resolvedProject: Project) {
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    assertThat(buildType.descriptor.testEnumerateProperties(), equalTo(PsBuildType.BuildTypeDescriptors.testEnumerateProperties()))
  }

  @Test
  fun testDescriptorGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestDescriptor(resolvedProject)
    }
  }

  @Test
  fun testDescriptorKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestDescriptor(resolvedProject)
    }
  }

  private fun doTestProperties(resolvedProject: Project) {
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
      assertThat(consumerProGuardFiles.resolved.asTestValue(), equalTo(listOf(File(appModule.rootDir!!,"other.pro"))))
    }
  }

  @Test
  fun testPropertiesGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestProperties(resolvedProject)
    }
  }

  @Test
  fun testPropertiesKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestProperties(resolvedProject)
    }
  }

  private fun doTestDefaultResolvedProperties(resolvedProject: Project) {
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

    assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
    assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf()))
  }

  @Test
  fun testDefaultResolvedPropertiesGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestDefaultResolvedProperties(resolvedProject)
    }
  }

  @Test
  fun testDefaultResolvedPropertiesKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestDefaultResolvedProperties(resolvedProject)
    }
  }

  private fun doTestSetProperties(resolvedProject: Project) {
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
        // Note: Resolved values of matchingFallbacks property are not available.
        // TODO(b/72814329): assertThat(proGuardFiles[1].parsedValue.asTestValue(), equalTo(proGuardFiles[1].resolved.asTestValue()))
        // TODO(b/72814329): assertThat(proGuardFiles[2].parsedValue.asTestValue(), equalTo(proGuardFiles[2].resolved.asTestValue()))

        assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
      }
    }

    verifyValues(buildType)

    appModule.applyChanges()
    resolvedProject.requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("release")!!, afterSync = true)
  }

  @Test
  fun testSetPropertiesGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestSetProperties(resolvedProject)
    }
  }

  @Test
  fun testSetPropertiesKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestSetProperties(resolvedProject)
    }
  }

  private fun doTestUndeclaredDebugSetProperties(resolvedProject: Project) {
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
    resolvedProject.requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("debug")!!, afterSync = true)
  }

  @Test
  fun testUndeclaredDebugSetPropertiesGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestUndeclaredDebugSetProperties(resolvedProject)
    }
  }

  @Test
  fun testUndeclaredDebugSetPropertiesKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestUndeclaredDebugSetProperties(resolvedProject)
    }
  }

  private fun doTestUndeclaredDebugEditLists(resolvedProject: Project) {
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
    resolvedProject.requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("debug")!!, afterSync = true)
  }

  @Test
  fun testUndeclaredDebugEditListsGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestUndeclaredDebugEditLists(resolvedProject)
    }
  }

  @Test
  fun testUndeclaredDebugEditListsKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestUndeclaredDebugEditLists(resolvedProject)
    }
  }

  private fun doTestUndeclaredDebugEditMaps(resolvedProject: Project) {
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
    resolvedProject.requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("debug")!!, afterSync = true)
  }

  @Test
  fun testUndeclaredDebugEditMapsGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestUndeclaredDebugEditMaps(resolvedProject)
    }
  }

  @Test
  fun testUndeclaredDebugEditMapsKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestUndeclaredDebugEditMaps(resolvedProject)
    }
  }

  private fun doTestInsertingProguardFiles(resolvedProject: Project) {
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
    resolvedProject.requestSyncAndWait()
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    appModule = project.findModuleByName("app") as PsAndroidModule
    // Verify nothing bad happened to the values after the re-parsing.
    verifyValues(appModule.findBuildType("release")!!, afterSync = true)
  }

  @Test
  fun testInsertingProguardFilesGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestInsertingProguardFiles(resolvedProject)
    }
  }

  @Test
  fun testInsertingProguardFilesKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestInsertingProguardFiles(resolvedProject)
    }
  }

  // TODO(b/240693165): Enable this test
  //@Test
  fun testSetListReferences() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->

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
      resolvedProject.requestSyncAndWait()
      project = PsProjectImpl(resolvedProject).also { it.testResolve() }
      appModule = project.findModuleByName("app") as PsAndroidModule
      // Verify nothing bad happened to the values after the re-parsing.
      verifyValues(appModule.findBuildType("release")!!, afterSync = true)
    }
  }
}
