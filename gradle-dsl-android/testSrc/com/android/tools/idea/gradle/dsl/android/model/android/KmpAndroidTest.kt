/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.android.model.android

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.android.api.android.CompileSdkReleaseModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.android.model.AndroidGradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.jetbrains.annotations.SystemDependent
import org.junit.Test

class KmpAndroidTest : AndroidGradleFileModelTestCase() {

  @Test
  fun testParseKmpAndroid() {
    isIrrelevantForGroovy("Kotlin Multiplatform modules are not available for Groovy")

    writeToBuildFile(TestFile.KMP_ANDROID)
    val kmpAndroidLibraryModel = gradleBuildModel.kotlin().android()

    assertEquals("namespace", "abc", kmpAndroidLibraryModel.namespace())

    val compileSdkVersion = kmpAndroidLibraryModel.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()

    val config = compileSdkVersion.toCompileSdkConfig()
    assertThat(config).isNotNull()

    val version = config!!.getVersion()
    assertThat(version).isNotNull()

    val release = (version as CompileSdkReleaseModel)

    assertThat(release.getVersion().getValue(
      GradlePropertyModel.INTEGER_TYPE)).isEqualTo(36)
    assertThat(release.getMinorApiLevel().getValue(
      GradlePropertyModel.INTEGER_TYPE)).isEqualTo(1)

    assertThat(kmpAndroidLibraryModel.minSdkVersion().getValue(
      GradlePropertyModel.INTEGER_TYPE)).isEqualTo(30)
  }

  @Test
  fun testParseKmpAndroidLibrary() {
    isIrrelevantForGroovy("Kotlin Multiplatform modules are not available for Groovy")

    writeToBuildFile(TestFile.KMP_ANDROID_LIBRARY)

    // Specifically using an AGP version older than 9.0.0
    gradleBuildModel.context.agpVersion = AndroidGradlePluginVersion.parse("8.12.0")
    val kmpAndroidLibraryModel = gradleBuildModel.kotlin().android()

    assertEquals("namespace", "abc", kmpAndroidLibraryModel.namespace())

    assertThat(kmpAndroidLibraryModel.compileSdkVersion().getValue(
      GradlePropertyModel.INTEGER_TYPE)).isEqualTo(36)

    assertThat(kmpAndroidLibraryModel.minSdkVersion().getValue(
      GradlePropertyModel.INTEGER_TYPE)).isEqualTo(30)
  }

  @Test
  fun testRemoveAndApply() {
    isIrrelevantForGroovy("Kotlin Multiplatform modules are not available for Groovy")

    writeToBuildFile(TestFile.KMP_ANDROID)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse("9.0.0")

    buildModel.kotlin().android().delete()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    buildModel.kotlin().android().namespace().setValue("abc")

    val config = buildModel.kotlin().android().compileSdkVersion().toCompileSdkConfig()
    assertThat(config).isNotNull()

    config!!.setReleaseVersion(36,1, null)

    buildModel.kotlin().android().minSdkVersion().setValue(30)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.KMP_ANDROID)
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    KMP_ANDROID("block"),
    KMP_ANDROID_LIBRARY("legacyBlock")
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/kotlinMultiplatform/$path", extension)
    }
  }
}