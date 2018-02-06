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

import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ResolvedValue
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

class PsAndroidModuleDefaultConfigDescriptorsTest : AndroidGradleTestCase() {

  private fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
  private fun <T> ParsedValue<T>.asTestValue(): T? = (this as? ParsedValue.Set.Parsed<T>)?.value
  private fun <T : Any> T.asParsed(): ParsedValue<T> = ParsedValue.Set.Parsed(value = this)

  fun testProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())
    val defaultConfig = appModule.defaultConfig

    val applicationId = PsAndroidModuleDefaultConfigDescriptors.applicationId.getValue(defaultConfig)
    val maxSdkVersion = PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion.getValue(defaultConfig)
    val minSdkVersion = PsAndroidModuleDefaultConfigDescriptors.minSdkVersion.getValue(defaultConfig)
    val multiDexEnabled = PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled.getValue(defaultConfig)
    val targetSdkVersion = PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion.getValue(defaultConfig)
    val testApplicationId = PsAndroidModuleDefaultConfigDescriptors.testApplicationId.getValue(defaultConfig)
    // TODO(b/70501607): Decide on val testFunctionalTest = PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest.getValue(defaultConfig)
    // TODO(b/70501607): Decide on val testHandleProfiling = PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling.getValue(defaultConfig)
    val testInstrumentationRunner = PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner.getValue(defaultConfig)
    val versionCode = PsAndroidModuleDefaultConfigDescriptors.versionCode.getValue(defaultConfig)
    val versionName = PsAndroidModuleDefaultConfigDescriptors.versionName.getValue(defaultConfig)
    val proGuardFiles = PsAndroidModuleDefaultConfigDescriptors.proGuardFiles.getValue(defaultConfig)
    val manifestPlaceholders = PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.getValue(defaultConfig)
    val editableManifestPlaceholders =
      PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.getEditableValues(defaultConfig)
        .mapValues { it.value.getValue(Unit) }

    assertThat(applicationId.resolved.asTestValue(), equalTo("com.example.psd.sample.app.default"))
    assertThat(applicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.default"))

    assertThat(maxSdkVersion.resolved.asTestValue(), equalTo(26))
    assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(26))

    assertThat(minSdkVersion.resolved.asTestValue(), equalTo("9"))
    assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo("9"))

    assertThat(multiDexEnabled.resolved.asTestValue(), nullValue())
    assertThat(multiDexEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(targetSdkVersion.resolved.asTestValue(), equalTo("19"))
    // TODO(b/71988818) assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo("19"))

    assertThat(testApplicationId.resolved.asTestValue(), equalTo("com.example.psd.sample.app.default.test"))
    assertThat(testApplicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.default.test"))

    assertThat(testInstrumentationRunner.resolved.asTestValue(), nullValue())
    assertThat(testInstrumentationRunner.parsedValue.asTestValue(), nullValue())

    assertThat(versionCode.resolved.asTestValue(), equalTo("1"))
    assertThat(versionCode.parsedValue.asTestValue(), equalTo("1"))

    assertThat(versionName.resolved.asTestValue(), equalTo("1.0"))
    assertThat(versionName.parsedValue.asTestValue(), equalTo("1.0"))

    assertThat(proGuardFiles.resolved.asTestValue(), equalTo(listOf()))
    assertThat(proGuardFiles.parsedValue.asTestValue(), nullValue())

    assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf("aa" to "aaa", "bb" to "bbb")))
    assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf("aa" to "aaa", "bb" to "bbb")))

    assertThat(editableManifestPlaceholders["aa"]?.resolved?.asTestValue(), equalTo("aaa"))
    assertThat(editableManifestPlaceholders["aa"]?.parsedValue?.asTestValue(), equalTo("aaa"))

    assertThat(editableManifestPlaceholders["bb"]?.resolved?.asTestValue(), equalTo("bbb"))
    assertThat(editableManifestPlaceholders["bb"]?.parsedValue?.asTestValue(), equalTo("bbb"))
  }

  fun testSetProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val defaultConfig = appModule.defaultConfig
    assertThat(defaultConfig, notNullValue()); defaultConfig!!

    defaultConfig.applicationId = "com.example.psd.sample.app.unpaid".asParsed()
    defaultConfig.maxSdkVersion = 26.asParsed()
    defaultConfig.minSdkVersion = "11".asParsed()
    defaultConfig.multiDexEnabled = true.asParsed()
    defaultConfig.targetSdkVersion = "21".asParsed()
    defaultConfig.testApplicationId = "com.example.psd.sample.app.unpaid.failed_test".asParsed()
    defaultConfig.testInstrumentationRunner = "com.runner".asParsed()
    defaultConfig.versionCode = "3".asParsed()
    defaultConfig.versionName = "3.0".asParsed()
    defaultConfig.manifestPlaceholders = mapOf("cc" to "CCC", "dd" to "NotDDD").asParsed()
    PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.getEditableValues(defaultConfig)["dd"]?.setParsedValue(Unit, "DDD".asParsed())

    fun verifyValues(defaultConfig: PsAndroidModuleDefaultConfig, afterSync: Boolean = false) {
      val applicationId = PsAndroidModuleDefaultConfigDescriptors.applicationId.getValue(defaultConfig)
      val maxSdkVersion = PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion.getValue(defaultConfig)
      val minSdkVersion = PsAndroidModuleDefaultConfigDescriptors.minSdkVersion.getValue(defaultConfig)
      val multiDexEnabled = PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled.getValue(defaultConfig)
      val targetSdkVersion = PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion.getValue(defaultConfig)
      val testApplicationId = PsAndroidModuleDefaultConfigDescriptors.testApplicationId.getValue(defaultConfig)
      // TODO(b/70501607): Decide on val testFunctionalTest = PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest.getValue(defaultConfig)
      // TODO(b/70501607): Decide on val testHandleProfiling = PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling.getValue(defaultConfig)
      val testInstrumentationRunner = PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner.getValue(defaultConfig)
      val versionCode = PsAndroidModuleDefaultConfigDescriptors.versionCode.getValue(defaultConfig)
      val versionName = PsAndroidModuleDefaultConfigDescriptors.versionName.getValue(defaultConfig)
      val manifestPlaceholders = PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders.getValue(defaultConfig)

      assertThat(applicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.unpaid"))
      assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(26))
      assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo("11"))
      assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(true))
      // TODO(b/71988818)
      assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo("21"))
      assertThat(testApplicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.unpaid.failed_test"))
      assertThat(testInstrumentationRunner.parsedValue.asTestValue(), equalTo("com.runner"))
      assertThat(versionCode.parsedValue.asTestValue(), equalTo("3"))
      assertThat(versionName.parsedValue.asTestValue(), equalTo("3.0"))
      assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(mapOf("cc" to "CCC", "dd" to "DDD")))

      if (afterSync) {
        assertThat(applicationId.parsedValue.asTestValue(), equalTo(applicationId.resolved.asTestValue()))
        assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(maxSdkVersion.resolved.asTestValue()))
        assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo(minSdkVersion.resolved.asTestValue()))
        assertThat(multiDexEnabled.parsedValue.asTestValue(), equalTo(multiDexEnabled.resolved.asTestValue()))
        // TODO(b/71988818)
        assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo(targetSdkVersion.resolved.asTestValue()))
        assertThat(testApplicationId.parsedValue.asTestValue(), equalTo(testApplicationId.resolved.asTestValue()))
        assertThat(testInstrumentationRunner.parsedValue.asTestValue(), equalTo(testInstrumentationRunner.resolved.asTestValue()))
        assertThat(versionCode.parsedValue.asTestValue(), equalTo(versionCode.resolved.asTestValue()))
        assertThat(versionName.parsedValue.asTestValue(), equalTo(versionName.resolved.asTestValue()))
        assertThat(manifestPlaceholders.parsedValue.asTestValue(), equalTo(manifestPlaceholders.resolved.asTestValue()))
      }
      verifyValues(defaultConfig)

      appModule.applyChanges()
      requestSyncAndWait()
      project = PsProject(resolvedProject)
      appModule = project.findModuleByName("app") as PsAndroidModule
      // Verify nothing bad happened to the values after the re-parsing.
      verifyValues(appModule.defaultConfig, afterSync = true)
    }
  }
}
