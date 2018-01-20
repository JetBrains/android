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

import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ResolvedValue
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE
import com.intellij.pom.java.LanguageLevel
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

class AndroidModuleDescriptorsTest : AndroidGradleTestCase() {

  private fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
  private fun <T> ParsedValue<T>.asTestValue(): T? = (this as? ParsedValue.Set.Parsed<T>)?.value

  fun testProperties() {
    loadProject(PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildToolsVersion = AndroidModuleDescriptors.buildToolsVersion.getValue(appModule)
    val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.getValue(appModule)
    val sourceCompatibility = AndroidModuleDescriptors.sourceCompatibility.getValue(appModule)
    val targetCompatibility = AndroidModuleDescriptors.targetCompatibility.getValue(appModule)

    assertThat(buildToolsVersion.resolved.asTestValue(), equalTo("27.0.3"))
    assertThat(buildToolsVersion.parsedValue.asTestValue(), equalTo("27.0.3"))

    assertThat(compileSdkVersion.resolved.asTestValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()))
    assertThat(compileSdkVersion.parsedValue.asTestValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()))

    assertThat(sourceCompatibility.resolved.asTestValue(), equalTo(LanguageLevel.JDK_1_7))
    assertThat(sourceCompatibility.parsedValue.asTestValue(), nullValue())

    assertThat(targetCompatibility.resolved.asTestValue(), equalTo(LanguageLevel.JDK_1_7))
    assertThat(targetCompatibility.parsedValue.asTestValue(), nullValue())
  }
}