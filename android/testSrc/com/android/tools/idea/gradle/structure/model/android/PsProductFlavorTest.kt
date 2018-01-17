/*
 * Copyright (C) 2017 The Android Open Source Project
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
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

class PsProductFlavorTest : AndroidGradleTestCase() {

  private fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
  private fun <T> ParsedValue<T>.asTestValue(): T? = (this as? ParsedValue.Set.Parsed<T>)?.value

  fun testProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val productFlavor = appModule.findProductFlavor("paid")
    assertThat(productFlavor, notNullValue()); productFlavor!!

    val applicationId = PsProductFlavor.ProductFlavorDescriptors.applicationId.getValue(productFlavor)
    val dimension = PsProductFlavor.ProductFlavorDescriptors.dimension.getValue(productFlavor)
    val maxSdkVersion = PsProductFlavor.ProductFlavorDescriptors.maxSdkVersion.getValue(productFlavor)
    val minSdkVersion = PsProductFlavor.ProductFlavorDescriptors.minSdkVersion.getValue(productFlavor)
    val multiDexEnabled = PsProductFlavor.ProductFlavorDescriptors.multiDexEnabled.getValue(productFlavor)
    val targetSdkVersion = PsProductFlavor.ProductFlavorDescriptors.targetSdkVersion.getValue(productFlavor)
    val testApplicationId = PsProductFlavor.ProductFlavorDescriptors.testApplicationId.getValue(productFlavor)
    // TODO(b/70501607): Decide on val testFunctionalTest = PsProductFlavor.ProductFlavorDescriptors.testFunctionalTest.getValue(productFlavor)
    // TODO(b/70501607): Decide on val testHandleProfiling = PsProductFlavor.ProductFlavorDescriptors.testHandleProfiling.getValue(productFlavor)
    val testInstrumentationRunner = PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunner.getValue(productFlavor)
    val versionCode = PsProductFlavor.ProductFlavorDescriptors.versionCode.getValue(productFlavor)
    val versionName = PsProductFlavor.ProductFlavorDescriptors.versionName.getValue(productFlavor)

    assertThat(dimension.resolved.asTestValue(), equalTo("foo"))
    assertThat(dimension.parsedValue.asTestValue(), equalTo("foo"))

    assertThat(applicationId.resolved.asTestValue(), equalTo("com.example.psd.sample.app.paid"))
    assertThat(applicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.paid"))

    assertThat(maxSdkVersion.resolved.asTestValue(), equalTo(25))
    assertThat(maxSdkVersion.parsedValue.asTestValue(), equalTo(25))

    assertThat(minSdkVersion.resolved.asTestValue(), equalTo("10"))
    assertThat(minSdkVersion.parsedValue.asTestValue(), equalTo("10"))

    assertThat(multiDexEnabled.resolved.asTestValue(), nullValue())
    assertThat(multiDexEnabled.parsedValue.asTestValue(), nullValue())

    assertThat(targetSdkVersion.resolved.asTestValue(), equalTo("20"))
    // TODO(b/71988818) assertThat(targetSdkVersion.parsedValue.asTestValue(), equalTo("19"))

    assertThat(testApplicationId.resolved.asTestValue(), equalTo("com.example.psd.sample.app.paid.test"))
    assertThat(testApplicationId.parsedValue.asTestValue(), equalTo("com.example.psd.sample.app.paid.test"))

    assertThat(testInstrumentationRunner.resolved.asTestValue(), nullValue())
    assertThat(testInstrumentationRunner.parsedValue.asTestValue(), nullValue())

    assertThat(versionCode.resolved.asTestValue(), equalTo("2"))
    assertThat(versionCode.parsedValue.asTestValue(), equalTo("2"))

    assertThat(versionName.resolved.asTestValue(), equalTo("2.0"))
    assertThat(versionName.parsedValue.asTestValue(), equalTo("2.0"))

  }
}
