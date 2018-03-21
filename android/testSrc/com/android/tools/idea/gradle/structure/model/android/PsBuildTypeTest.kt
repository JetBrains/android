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

class PsBuildTypeTest : AndroidGradleTestCase() {

  private fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
  private fun <T> ParsedValue<T>.asTestValue(): T? = (this as? ParsedValue.Set.Parsed<T>)?.value

  fun testProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("release")
    assertThat(buildType, notNullValue()); buildType!!

    val applicationIdSuffix = PsBuildType.BuildTypeDescriptors.applicationIdSuffix.getValue(buildType)
    val debuggable = PsBuildType.BuildTypeDescriptors.debuggable.getValue(buildType)
    // TODO(b/70501607): Decide on val embedMicroApp = PsBuildType.BuildTypeDescriptors.embedMicroApp.getValue(buildType)
    val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.getValue(buildType)
    val minifyEnabled = PsBuildType.BuildTypeDescriptors.minifyEnabled.getValue(buildType)
    val multiDexEnabled = PsBuildType.BuildTypeDescriptors.multiDexEnabled.getValue(buildType)
    // TODO(b/70501607): Decide on val pseudoLocalesEnabled = PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled.getValue(buildType)
    val renderscriptDebuggable = PsBuildType.BuildTypeDescriptors.renderscriptDebuggable.getValue(buildType)
    val renderscriptOptimLevel = PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel.getValue(buildType)
    // TODO(b/70501607): Decide on val testCoverageEnabled = PsBuildType.BuildTypeDescriptors.testCoverageEnabled.getValue(buildType)
    val versionNameSuffix = PsBuildType.BuildTypeDescriptors.versionNameSuffix.getValue(buildType)
    val zipAlignEnabled = PsBuildType.BuildTypeDescriptors.zipAlignEnabled.getValue(buildType)

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

    assertThat(versionNameSuffix.resolved.asTestValue(), equalTo("vsuffix"))
    assertThat(versionNameSuffix.parsedValue.asTestValue(), equalTo("vsuffix"))

    assertThat(zipAlignEnabled.resolved.asTestValue(), equalTo(true))
    assertThat(zipAlignEnabled.parsedValue.asTestValue(), nullValue())
  }

  fun testProperties_defaultResolved() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val buildType = appModule.findBuildType("debug")
    assertThat(buildType, notNullValue()); buildType!!
    assertFalse(buildType.isDeclared)

    val applicationIdSuffix = PsBuildType.BuildTypeDescriptors.applicationIdSuffix.getValue(buildType)
    val debuggable = PsBuildType.BuildTypeDescriptors.debuggable.getValue(buildType)
    // TODO(b/70501607): Decide on val embedMicroApp = PsBuildType.BuildTypeDescriptors.embedMicroApp.getValue(buildType)
    val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.getValue(buildType)
    val minifyEnabled = PsBuildType.BuildTypeDescriptors.minifyEnabled.getValue(buildType)
    val multiDexEnabled = PsBuildType.BuildTypeDescriptors.multiDexEnabled.getValue(buildType)
    // TODO(b/70501607): Decide on val pseudoLocalesEnabled = PsBuildType.BuildTypeDescriptors.pseudoLocalesEnabled.getValue(buildType)
    val renderscriptDebuggable = PsBuildType.BuildTypeDescriptors.renderscriptDebuggable.getValue(buildType)
    val renderscriptOptimLevel = PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel.getValue(buildType)
    // TODO(b/70501607): Decide on val testCoverageEnabled = PsBuildType.BuildTypeDescriptors.testCoverageEnabled.getValue(buildType)
    val versionNameSuffix = PsBuildType.BuildTypeDescriptors.versionNameSuffix.getValue(buildType)
    val zipAlignEnabled = PsBuildType.BuildTypeDescriptors.zipAlignEnabled.getValue(buildType)

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

    assertThat(versionNameSuffix.resolved.asTestValue(), nullValue())
    assertThat(versionNameSuffix.parsedValue.asTestValue(), nullValue())

    assertThat(zipAlignEnabled.resolved.asTestValue(), equalTo(true))
    assertThat(zipAlignEnabled.parsedValue.asTestValue(), nullValue())
  }
}