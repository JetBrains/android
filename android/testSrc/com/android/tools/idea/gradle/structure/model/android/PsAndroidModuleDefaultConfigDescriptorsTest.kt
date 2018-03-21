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
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

class PsAndroidModuleDefaultConfigDescriptorsTest : AndroidGradleTestCase() {

  private fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
  private fun <T> ParsedValue<T>.asTestValue(): T? = (this as? ParsedValue.Set.Parsed<T>)?.value

  fun testProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val applicationId = PsAndroidModuleDefaultConfigDescriptors.applicationId.getValue(appModule)
    val maxSdkVersion = PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion.getValue(appModule)
    val minSdkVersion = PsAndroidModuleDefaultConfigDescriptors.minSdkVersion.getValue(appModule)
    val multiDexEnabled = PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled.getValue(appModule)
    val targetSdkVersion = PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion.getValue(appModule)
    val testApplicationId = PsAndroidModuleDefaultConfigDescriptors.testApplicationId.getValue(appModule)
    // TODO(b/70501607): Decide on val testFunctionalTest = PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest.getValue(appModule)
    // TODO(b/70501607): Decide on val testHandleProfiling = PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling.getValue(appModule)
    val testInstrumentationRunner = PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner.getValue(appModule)
    val versionCode = PsAndroidModuleDefaultConfigDescriptors.versionCode.getValue(appModule)
    val versionName = PsAndroidModuleDefaultConfigDescriptors.versionName.getValue(appModule)

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
  }
}