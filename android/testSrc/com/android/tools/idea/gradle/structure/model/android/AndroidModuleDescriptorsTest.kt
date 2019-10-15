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
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.helpers.matchHashStrings
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE_GROOVY
import com.intellij.pom.java.LanguageLevel
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

class AndroidModuleDescriptorsTest : AndroidGradleTestCase() {

  fun testDescriptor() {
    loadProject(PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule.descriptor.testEnumerateProperties(), equalTo(AndroidModuleDescriptors.testEnumerateProperties()))
  }

  fun testProperties() {
    loadProject(PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildToolsVersion = AndroidModuleDescriptors.buildToolsVersion.bind(appModule).getValue()
    val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule).getValue()
    val sourceCompatibility = AndroidModuleDescriptors.sourceCompatibility.bind(appModule).getValue()
    val targetCompatibility = AndroidModuleDescriptors.targetCompatibility.bind(appModule).getValue()
    val viewBindingEnabled = AndroidModuleDescriptors.viewBindingEnabled.bind(appModule).getValue()

    assertThat(buildToolsVersion.resolved.asTestValue(), equalTo(SdkConstants.CURRENT_BUILD_TOOLS_VERSION))
    assertThat(buildToolsVersion.parsedValue.asTestValue(), equalTo(SdkConstants.CURRENT_BUILD_TOOLS_VERSION))

    assertThat(matchHashStrings(null, compileSdkVersion.resolved.asTestValue(), SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()),
               equalTo(true))
    assertThat(compileSdkVersion.parsedValue.asTestValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()))

    assertThat(sourceCompatibility.resolved.asTestValue(), equalTo(LanguageLevel.JDK_1_7))
    assertThat(sourceCompatibility.parsedValue.asTestValue(), nullValue())

    assertThat(targetCompatibility.resolved.asTestValue(), equalTo(LanguageLevel.JDK_1_7))
    assertThat(targetCompatibility.parsedValue.asTestValue(), nullValue())

    assertThat(viewBindingEnabled.resolved.asTestValue(), equalTo(false))
    assertThat(viewBindingEnabled.parsedValue.asTestValue(), nullValue())
  }

  fun testSetProperties() {
    // Note: this test does not attempt to sync because it won't succeed without installing older SDKs.
    loadProject(PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    appModule.compileSdkVersion = "25".asParsed()
    appModule.viewBindingEnabled = true.asParsed()

    fun verifyValues(appModule: PsAndroidModule) {
      val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule).getValue()
      val viewBindingEnabled = AndroidModuleDescriptors.viewBindingEnabled.bind(appModule).getValue()
      assertThat(compileSdkVersion.parsedValue.asTestValue(), equalTo("25"))
      assertThat(viewBindingEnabled.parsedValue.asTestValue(), equalTo(true))
      assertThat(appModule.parsedModel?.android()?.compileSdkVersion()?.getValue(OBJECT_TYPE), equalTo<Any>(25))
      assertThat(appModule.parsedModel?.android()?.viewBinding()?.enabled()?.getValue(OBJECT_TYPE), equalTo<Any>(true))
    }

    verifyValues(appModule)
    appModule.applyChanges()
    verifyValues(appModule)
  }
}
