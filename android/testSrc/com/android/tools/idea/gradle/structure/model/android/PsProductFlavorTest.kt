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
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

class PsProductFlavorTest : AndroidGradleTestCase() {

  private fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
  private fun <T> ParsedValue<T>.asTestValue(): T? = (this as? ParsedValue.Set.Parsed<T>)?.value
  private fun <T : Any> T.asParsed(): ParsedValue<T> = ParsedValue.Set.Parsed(value = this)

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
    val manifestPlaceholders = PsProductFlavor.ProductFlavorDescriptors.manifestPlaceholders.getValue(productFlavor)
    val testInstrumentationRunnerArguments =
      PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.getValue(productFlavor)
    val editableTestInstrumentationRunnerArguments =
      PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.getEditableValues(productFlavor)
        .mapValues { it.value.getValue(Unit) }

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

    assertThat(manifestPlaceholders.resolved.asTestValue(), equalTo(mapOf()))
    assertThat(manifestPlaceholders.parsedValue.asTestValue(), nullValue())

    assertThat(testInstrumentationRunnerArguments.resolved.asTestValue(), equalTo(mapOf("a" to "AAA", "b" to "BBB")))
    assertThat(testInstrumentationRunnerArguments.parsedValue.asTestValue(), equalTo(mapOf("a" to "AAA", "b" to "BBB")))

    assertThat(editableTestInstrumentationRunnerArguments["a"]?.resolved?.asTestValue(), equalTo("AAA"))
    assertThat(editableTestInstrumentationRunnerArguments["a"]?.parsedValue?.asTestValue(), equalTo("AAA"))

    assertThat(editableTestInstrumentationRunnerArguments["b"]?.resolved?.asTestValue(), equalTo("BBB"))
    assertThat(editableTestInstrumentationRunnerArguments["b"]?.parsedValue?.asTestValue(), equalTo("BBB"))
  }

  fun testDimensions() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val productFlavor = appModule.findProductFlavor("paid")
    assertThat(productFlavor, notNullValue()); productFlavor!!

    assertThat(
      PsProductFlavor.ProductFlavorDescriptors.dimension.getKnownValues(productFlavor),
      hasItems(ValueDescriptor("foo", "foo"), ValueDescriptor("bar", "bar")))
  }

  fun testSetProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    var project = PsProject(resolvedProject)

    var appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val productFlavor = appModule.findProductFlavor("paid")
    assertThat(productFlavor, notNullValue()); productFlavor!!

    productFlavor.applicationId = "com.example.psd.sample.app.unpaid".asParsed()
    productFlavor.dimension = "bar".asParsed()
    productFlavor.maxSdkVersion = 26.asParsed()
    productFlavor.minSdkVersion = "11".asParsed()
    productFlavor.multiDexEnabled = true.asParsed()
    productFlavor.targetSdkVersion = "21".asParsed()
    productFlavor.testApplicationId = "com.example.psd.sample.app.unpaid.failed_test".asParsed()
    productFlavor.testInstrumentationRunner = "com.runner".asParsed()
    productFlavor.versionCode = "3".asParsed()
    productFlavor.versionName = "3.0".asParsed()
    productFlavor.manifestPlaceholders = mapOf("c" to "CCC", "d" to "NotDDD").asParsed()
    PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments
      .getEditableValues(productFlavor)["d"]?.setParsedValue(Unit, "DDD".asParsed())

    fun verifyValues(productFlavor: PsProductFlavor, afterSync: Boolean = false) {
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
      val testInstrumentationRunnerArguments =
        PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments.getValue(productFlavor)

      assertThat(dimension.parsedValue.asTestValue(), equalTo("bar"))
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
      assertThat(testInstrumentationRunnerArguments.parsedValue.asTestValue(), equalTo(mapOf("c" to "CCC", "d" to "DDD")))

      if (afterSync) {
        assertThat(dimension.parsedValue.asTestValue(), equalTo(dimension.resolved.asTestValue()))
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
        assertThat(
          testInstrumentationRunnerArguments.parsedValue.asTestValue(),
          equalTo(testInstrumentationRunnerArguments.resolved.asTestValue())
        )
      }
      verifyValues(productFlavor)

      appModule.applyChanges()
      requestSyncAndWait()
      project = PsProject(resolvedProject)
      appModule = project.findModuleByName("app") as PsAndroidModule
      // Verify nothing bad happened to the values after the re-parsing.
      verifyValues(appModule.findProductFlavor("paid")!!, afterSync = true)
    }
  }
}
