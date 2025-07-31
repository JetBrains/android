/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.android.CompileSdkPropertyModel
import com.android.tools.idea.gradle.dsl.api.android.CompileSdkReleaseModel
import com.android.tools.idea.gradle.dsl.api.android.CompileSdkPreviewModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion
import com.google.common.truth.Truth.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class CompileSdkModelTest: GradleFileModelTestCase() {

  @Test
  fun testReadCompileSdkVersionBlock() {
    writeToBuildFile(TestFile.READ_RELEASE_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    val config = compileSdkVersion.toCompileSdkConfig()
    assertThat(config).isNotNull()
    val version = config!!.getVersion()
    assertThat(version).isNotNull()
    assertThat(version).isInstanceOf(CompileSdkReleaseModel::class.java)
    val release = (version as CompileSdkReleaseModel)
    assertThat(release.getVersion().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(33)
    assertThat(release.getMinorApiLevel().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(1)
    assertThat(release.getSdkExtension().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(18)
  }

  @Test
  fun testReadCompileSdkVersionReleaseMethod() {
    writeToBuildFile(TestFile.READ_RELEASE_METHOD)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    val config = compileSdkVersion.toCompileSdkConfig()
    assertThat(config).isNotNull()
    val version = config!!.getVersion()
    assertThat(version).isNotNull()
    assertThat(version).isInstanceOf(CompileSdkReleaseModel::class.java)
    val release = (version as CompileSdkReleaseModel)
    assertThat(release.getVersion().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(33)
    assertThat(release.getMinorApiLevel().getValue(GradlePropertyModel.INTEGER_TYPE)).isNull()
    assertThat(release.getSdkExtension().getValue(GradlePropertyModel.INTEGER_TYPE)).isNull()
  }


  @Test
  fun testReadCompileSdkVersionPreviewMethod() {
    writeToBuildFile(TestFile.READ_PREVIEW_METHOD)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    val config = compileSdkVersion.toCompileSdkConfig()
    assertThat(config).isNotNull()
    val version = config!!.getVersion()
    assertThat(version).isNotNull()
    assertThat(version).isInstanceOf(CompileSdkPreviewModel::class.java)
    val preview = (version as CompileSdkPreviewModel)
    assertThat(preview.getVersion().getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("Tiramisu")
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    READ_RELEASE_BLOCK("releaseBlock"),
    READ_RELEASE_METHOD("releaseMethod"),
    READ_PREVIEW_METHOD("previewMethod"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/compileSdk/$path", extension)
    }
  }
}