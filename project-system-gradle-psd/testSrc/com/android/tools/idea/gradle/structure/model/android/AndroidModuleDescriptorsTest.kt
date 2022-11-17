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

import com.android.SdkConstants
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.OBJECT_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.helpers.matchHashStrings
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.withoutKtsRelatedIndexing
import com.google.common.truth.Expect
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.RunsInEdt
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AndroidModuleDescriptorsTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @Test
  fun testDescriptor() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule.descriptor.testEnumerateProperties(), equalTo(AndroidModuleDescriptors.testEnumerateProperties()))
    }
  }

  @Test
  fun testProperties() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule, notNullValue())

      val buildToolsVersion = AndroidModuleDescriptors.buildToolsVersion.bind(appModule).getValue()
      val ndkVersion = AndroidModuleDescriptors.ndkVersion.bind(appModule).getValue()
      val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule).getValue()
      val sourceCompatibility = AndroidModuleDescriptors.sourceCompatibility.bind(appModule).getValue()
      val targetCompatibility = AndroidModuleDescriptors.targetCompatibility.bind(appModule).getValue()
      val viewBindingEnabled = AndroidModuleDescriptors.viewBindingEnabled.bind(appModule).getValue()

      assertThat(buildToolsVersion.resolved.asTestValue(), equalTo(SdkConstants.CURRENT_BUILD_TOOLS_VERSION))
      assertThat(buildToolsVersion.parsedValue.asTestValue(), equalTo(SdkConstants.CURRENT_BUILD_TOOLS_VERSION))

      assertThat(ndkVersion.resolved.asTestValue(), nullValue())
      assertThat(ndkVersion.parsedValue.asTestValue(), nullValue())

      assertThat(
        matchHashStrings(null, compileSdkVersion.resolved.asTestValue(), SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()),
        equalTo(true)
      )
      assertThat(compileSdkVersion.parsedValue.asTestValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()))

      assertThat(sourceCompatibility.resolved.asTestValue(), equalTo(LanguageLevel.JDK_1_8))
      assertThat(sourceCompatibility.parsedValue.asTestValue(), nullValue())

      assertThat(targetCompatibility.resolved.asTestValue(), equalTo(LanguageLevel.JDK_1_8))
      assertThat(targetCompatibility.parsedValue.asTestValue(), nullValue())

      assertThat(viewBindingEnabled.resolved.asTestValue(), equalTo(false))
      assertThat(viewBindingEnabled.parsedValue.asTestValue(), nullValue())
    }
  }

  private fun doTestSetProperties(resolvedProject: Project) {
    // Note: this test does not attempt to sync because it won't succeed without installing older SDKs.
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    appModule.compileSdkVersion = "25".asParsed()
    appModule.viewBindingEnabled = true.asParsed()
    appModule.includeDependenciesInfoInApk = false.asParsed()
    appModule.buildToolsVersion = ParsedValue.Set.Parsed(dslText = DslText.Reference("varProGuardFiles[0]"), value = null)

    fun verifyValues(appModule: PsAndroidModule) {
      val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule).getValue()
      val viewBindingEnabled = AndroidModuleDescriptors.viewBindingEnabled.bind(appModule).getValue()
      val includeDependenciesInfoInApk = AndroidModuleDescriptors.includeDependenciesInfoInApk.bind(appModule).getValue()
      assertThat(compileSdkVersion.parsedValue.asTestValue(), equalTo("25"))
      assertThat(viewBindingEnabled.parsedValue.asTestValue(), equalTo(true))
      assertThat(includeDependenciesInfoInApk.parsedValue.asTestValue(), equalTo(false))
      assertThat(appModule.parsedModel?.android()?.compileSdkVersion()?.getValue(OBJECT_TYPE), equalTo<Any>(25))
      assertThat(appModule.parsedModel?.android()?.viewBinding()?.enabled()?.getValue(OBJECT_TYPE), equalTo<Any>(true))
    }

    verifyValues(appModule)
    appModule.applyChanges()
    verifyValues(appModule)
  }

  @Test
  fun testSetPropertiesKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestSetProperties(resolvedProject)
    }
  }

  @Test
  fun testSetPropertiesGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestSetProperties(resolvedProject)
    }
  }

  @Test
  fun testSetListReferencesKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      val expectedKtsRawValues = listOf("localList[0]", "(rootProject.extra[\"listProp\"] as List<*>)[0] as Int")
      doTestSetListReferences(resolvedProject, expectedKtsRawValues)
    }
  }

  @Test
  fun testSetListReferencesGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val expectedGrRawValues = listOf("localList[0]", "listProp[0]")
      doTestSetListReferences(resolvedProject, expectedGrRawValues)
    }
  }

  private fun doTestSetListReferences(resolvedProject: Project, expectedValues: List<String>) {
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())


    // Set reference to a list extra property from same module.
    appModule.buildToolsVersion = ParsedValue.Set.Parsed(dslText = DslText.Reference("localList[0]"), value = null)
    // Set reference to a list extra property defined in rootProject build script.
    appModule.compileSdkVersion = ParsedValue.Set.Parsed(dslText = DslText.Reference("listProp[0]"), value = null)

    appModule.applyChanges()

    // Verify changes applied correctly.
    val buildToolsVersion = AndroidModuleDescriptors.buildToolsVersion.bind(appModule).getValue()
    val ndkVersion = AndroidModuleDescriptors.ndkVersion.bind(appModule).getValue()
    val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule).getValue()
    assertThat(buildToolsVersion.parsedValue.asTestValue(), equalTo("26.1.1"))
    assertNull(ndkVersion.parsedValue.asTestValue())
    assertThat(compileSdkVersion.parsedValue.asTestValue(), equalTo("15"))
    assertThat(appModule.parsedModel?.android()?.buildToolsVersion()?.getValue(OBJECT_TYPE), equalTo<Any>("26.1.1"))
    assertNull(appModule.parsedModel?.android()?.ndkVersion()?.getValue(OBJECT_TYPE))
    assertThat(appModule.parsedModel?.android()?.buildToolsVersion()?.getRawValue(STRING_TYPE), equalTo<Any>(expectedValues[0]))

    assertThat(appModule.parsedModel?.android()?.compileSdkVersion()?.getValue(OBJECT_TYPE), equalTo<Any>(15))
    assertThat(appModule.parsedModel?.android()?.compileSdkVersion()?.getRawValue(STRING_TYPE), equalTo<Any>(expectedValues[1]))
  }

  fun doTestSetDependencyReferenceVersion(resolvedProject: Project, expectedValues: List<String>) {
    val project = PsProjectImpl(resolvedProject)
    val appModule = project.findModuleByName("app") as PsAndroidModule
    var existingAgpDependency =
      appModule
        .parsedModel
        ?.dependencies()
        ?.artifacts()

    assertTrue(existingAgpDependency != null && existingAgpDependency.size == 4)
    ReferenceTo.createReferenceFromText("myVariable", existingAgpDependency!![0].version())?.let {
      existingAgpDependency!![0].version().setValue(it)
    }
    ReferenceTo.createReferenceFromText("versionVal", existingAgpDependency[1].version())?.let {
      existingAgpDependency!![1].version().setValue(it)
    }
    ReferenceTo.createReferenceFromText("localList[0]", existingAgpDependency[2].version())?.let {
      existingAgpDependency!![2].version().setValue(it)
    }
    ReferenceTo.createReferenceFromText("dependencyVersion", existingAgpDependency[3].version())?.let {
      existingAgpDependency!![3].version().setValue(it)
    }

    project.applyChanges()

    existingAgpDependency =
      appModule
        .parsedModel
        ?.dependencies()
        ?.artifacts()

    assertTrue(existingAgpDependency != null && existingAgpDependency.size == 4)
    assertThat(existingAgpDependency!![0].compactNotation(), equalTo("com.android.support:appcompat-v7:26.1.0"))
    assertThat(
      existingAgpDependency[0].completeModel().getRawValue(STRING_TYPE),
      equalTo<String>("com.android.support:appcompat-v7:${expectedValues[0]}"))

    assertThat(
      existingAgpDependency[1].compactNotation(),
      equalTo("com.android.support.constraint:constraint-layout:28.0.0"))
    assertThat(
      existingAgpDependency[1].completeModel().getRawValue(STRING_TYPE),
      equalTo<String>("com.android.support.constraint:constraint-layout:${expectedValues[1]}"))

    assertThat(
      existingAgpDependency[2].compactNotation(),
      equalTo("com.android.support.test:runner:26.1.1"))
    assertThat(
      existingAgpDependency[2].completeModel().getRawValue(STRING_TYPE),
      equalTo<String>("com.android.support.test:runner:${expectedValues[2]}"))

    assertThat(
      existingAgpDependency[3].compactNotation(),
      equalTo("com.android.support.test.espresso:espresso-core:28.0.0"))
    assertThat(
      existingAgpDependency[3].completeModel().getRawValue(STRING_TYPE),
      equalTo<String>("com.android.support.test.espresso:espresso-core:${expectedValues[3]}"))
  }

  @Test
  fun testSetDependencyReferenceVersionGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val expectedValues = listOf("${'$'}myVariable", "${'$'}versionVal", "${'$'}{localList[0]}", "${'$'}dependencyVersion")
      doTestSetDependencyReferenceVersion(resolvedProject, expectedValues)
    }
  }

  @Test
  fun testSetDependencyReferenceVersionKts() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      val expectedValues =
        listOf(
          "${'$'}myVariable",
          "${'$'}{project.extra[\"versionVal\"]}",
          "${'$'}{localList[0]}",
          "${'$'}{rootProject.extra[\"dependencyVersion\"]}"
        )
      doTestSetDependencyReferenceVersion(resolvedProject, expectedValues)
    }
  }
}
