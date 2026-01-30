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
package com.android.tools.idea.gradle.dsl.model.build

import com.android.tools.idea.gradle.dsl.TestFileNameImpl
import com.android.tools.idea.gradle.dsl.android.api.android.CompileSdkPreviewModel
import com.android.tools.idea.gradle.dsl.android.api.android.CompileSdkReleaseModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.android.model.android.android
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.readText
import kotlin.jvm.java
import org.junit.Test

class VersionCatalogReferenceResolutionTest : GradleFileModelTestCase() {
  @Test
  @Throws(Exception::class)
  fun testResolveCatalogReference() {
    writeToVersionCatalogFile(
      """
      [versions]
      version = "Tiramisu"
      """
        .trimIndent()
    )
    writeToBuildFile(TestFileNameImpl.REFERENCE_RESOLUTION_FROM_VERSION_CATALOG)

    // test reading
    val projectModel = getProjectBuildModel()
    val gradleModel = projectModel.projectBuildModel!!
    val android = gradleModel.android()
    assertNotNull(android)
    val propertyModel = android.compileSdkVersion()
    assertThat(propertyModel.toCompileSdkConfig()).isNotNull()
    val version = propertyModel.toCompileSdkConfig()!!.getVersion()
    assertThat(version).isInstanceOf(CompileSdkPreviewModel::class.java)
    val preview = (version as CompileSdkPreviewModel)
    assertThat(preview.getVersion().valueType).isEqualTo(GradlePropertyModel.ValueType.STRING)
    assertThat(preview.getVersion().getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("Tiramisu")

    // update value
    preview.getVersion().setValue("xyz")
    assertThat(preview.getVersion().getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("xyz")
    WriteCommandAction.runWriteCommandAction(project) { projectModel.applyChanges() }
    // reread value again
    val newAndroid = getGradleBuildModel().android()
    assertNotNull(newAndroid)
    val newPropertyModel = newAndroid.compileSdkVersion()
    assertThat(newPropertyModel.toCompileSdkConfig()).isNotNull()
    val newVersion = newPropertyModel.toCompileSdkConfig()!!.getVersion()
    val newPreview = (newVersion as CompileSdkPreviewModel)
    assertThat(newPreview.getVersion().getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("xyz")

    assertThat(myVersionCatalogFile.readText().replace(" ", "")).contains("version=\"xyz\"")
  }

  @Test
  @Throws(Exception::class)
  fun testResolveCatalogReferenceWithToInteger() {
    writeToVersionCatalogFile(
      """
      [versions]
      version = "1"
      """
        .trimIndent()
    )
    writeToBuildFile(TestFileNameImpl.REFERENCE_RESOLUTION_FROM_VERSION_CATALOG_TO_INT)

    val projectModel = getProjectBuildModel()
    val gradleModel = projectModel.projectBuildModel!!
    val android = gradleModel.android()
    assertNotNull(android)
    val propertyModel = android.compileSdkVersion()
    assertThat(propertyModel.toCompileSdkConfig()).isNotNull()
    val version = propertyModel.toCompileSdkConfig()!!.getVersion()
    assertThat(version).isInstanceOf(CompileSdkReleaseModel::class.java)
    val release = (version as CompileSdkReleaseModel)
    assertThat(release.getVersion().valueType).isEqualTo(GradlePropertyModel.ValueType.INTEGER)
    assertThat(release.getVersion().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(1)

    release.getVersion().setValue(2)
    assertThat(release.getVersion().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(2)

    WriteCommandAction.runWriteCommandAction(project) { projectModel.applyChanges() }
    val newAndroid = getGradleBuildModel().android()
    assertNotNull(newAndroid)
    val newPropertyModel = newAndroid.compileSdkVersion()
    assertThat(newPropertyModel.toCompileSdkConfig()).isNotNull()
    val newVersion = newPropertyModel.toCompileSdkConfig()!!.getVersion()
    val newRelease = (newVersion as CompileSdkReleaseModel)
    assertThat(newRelease.getVersion().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(2)

    assertThat(myVersionCatalogFile.readText().replace(" ", "")).contains("version=\"2\"")
  }

  @Test
  @Throws(Exception::class)
  fun testResolveCatalogReferenceInAssignment() {
    writeToVersionCatalogFile(
      """
      [versions]
      version = "Tiramisu"
      """
        .trimIndent()
    )
    writeToBuildFile(TestFileNameImpl.REFERENCE_RESOLUTION_FROM_VERSION_CATALOG_ASSIGNMENT)

    val android = getGradleBuildModel().android()
    assertNotNull(android)
    val propertyModel = android.compileSdkVersion()
    assertThat(propertyModel).isNotNull()
    assertThat(propertyModel.valueType).isEqualTo(GradlePropertyModel.ValueType.STRING)
    assertThat(propertyModel.toString()).isEqualTo("Tiramisu")
  }

  @Test
  @Throws(Exception::class)
  fun testResolveCatalogReferenceWithInAssignmentInt() {
    writeToVersionCatalogFile(
      """
      [versions]
      version = "1"
      """
        .trimIndent()
    )
    writeToBuildFile(TestFileNameImpl.REFERENCE_RESOLUTION_FROM_VERSION_CATALOG_ASSIGNMENT_INT)

    val android = getGradleBuildModel().android()
    assertNotNull(android)
    val propertyModel = android.compileSdkVersion()
    assertThat(propertyModel).isNotNull()
    assertThat(propertyModel.valueType).isEqualTo(GradlePropertyModel.ValueType.INTEGER)
    assertThat(propertyModel.toInt()).isEqualTo(1)
  }

  @Test
  @Throws(Exception::class)
  fun testResolveCatalogReferenceWithInterpolate() {
    writeToVersionCatalogFile(
      """
      [versions]
      version = "34"
      """
        .trimIndent()
    )
    writeToBuildFile(TestFileNameImpl.REFERENCE_RESOLUTION_FROM_VERSION_CATALOG_ASSIGNMENT_INTERPOLATE)

    val android = getGradleBuildModel().android()
    assertNotNull(android)
    val propertyModel = android.compileSdkVersion()
    assertThat(propertyModel).isNotNull()
    assertThat(propertyModel.valueType).isEqualTo(GradlePropertyModel.ValueType.INTERPOLATED)
    assertThat(propertyModel.toString()).isEqualTo("android-34")
  }
}
