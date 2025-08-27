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
import com.android.tools.idea.gradle.dsl.api.android.CompileSdkAddonModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion
import com.google.common.truth.Truth.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo

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
    assertThat(compileSdkVersion.getRawValue(GradlePropertyModel.OBJECT_TYPE)).isEqualTo("android-33.1-ext18")
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
    assertThat(compileSdkVersion.getRawValue(GradlePropertyModel.OBJECT_TYPE)).isEqualTo("android-33")
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
  fun testReadVariableInCompileSdkVersionReleaseMethod() {
    writeToBuildFile(TestFile.READ_RELEASE_METHOD_WITH_REFERENCE)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(30)
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("android-30")
    val config = compileSdkVersion.toCompileSdkConfig()
    assertThat(config).isNotNull()
    val version = config!!.getVersion()
    assertThat(version).isNotNull()
    assertThat(version).isInstanceOf(CompileSdkReleaseModel::class.java)
    val release = (version as CompileSdkReleaseModel)
    assertThat(release.getVersion().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(30)
  }

  @Test
  fun testSetReferenceInCompileSdkVersionReleaseMethod() {
    writeToBuildFile(TestFile.SET_RELEASE_METHOD_TO_REFERENCE)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    android.compileSdkVersion().setValue(ReferenceTo(buildModel.ext().findProperty("sdkVersion")))
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_RELEASE_METHOD_TO_REFERENCE_EXPECTED)
  }

  @Test
  fun testReadVariableInCompileSdkVersionPreviewMethod() {
    writeToBuildFile(TestFile.READ_PREVIEW_METHOD_WITH_REFERENCE)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("Tiramisu")
    val config = compileSdkVersion.toCompileSdkConfig()
    assertThat(config).isNotNull()
    val version = config!!.getVersion()
    assertThat(version).isNotNull()
    assertThat(version).isInstanceOf(CompileSdkPreviewModel::class.java)
    val preview = (version as CompileSdkPreviewModel)
    assertThat(preview.getVersion().getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("Tiramisu")
  }

  @Test
  fun testSetReferenceInCompileSdkVersionPreviewMethod() {
    writeToBuildFile(TestFile.SET_PREVIEW_METHOD_TO_REFERENCE)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    android.compileSdkVersion().setValue(ReferenceTo(buildModel.ext().findProperty("sdkVersion")))
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_PREVIEW_METHOD_TO_REFERENCE_EXPECTED)
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
    assertThat(compileSdkVersion.getRawValue(GradlePropertyModel.OBJECT_TYPE)).isEqualTo("Tiramisu")
    val config = compileSdkVersion.toCompileSdkConfig()
    assertThat(config).isNotNull()
    val version = config!!.getVersion()
    assertThat(version).isNotNull()
    assertThat(version).isInstanceOf(CompileSdkPreviewModel::class.java)
    val preview = (version as CompileSdkPreviewModel)
    assertThat(preview.getVersion().getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("Tiramisu")
  }

  @Test
  fun testReadCompileSdkVersionAddonMethod() {
    writeToBuildFile(TestFile.READ_ADDON_METHOD)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    assertThat(compileSdkVersion.getRawValue(GradlePropertyModel.OBJECT_TYPE)).isEqualTo("vendor:addon:1")
    val config = compileSdkVersion.toCompileSdkConfig()
    assertThat(config).isNotNull()
    val version = config!!.getVersion()
    assertThat(version).isNotNull()
    assertThat(version).isInstanceOf(CompileSdkAddonModel::class.java)
    val addon = (version as CompileSdkAddonModel)
    assertThat(addon.getVendorName().getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("vendor")
    assertThat(addon.getAddonName().getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("addon")
    assertThat(addon.getVersion().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(1)
  }

  @Test
  fun testUpdateCompileSdkVersionWithOldApi() {
    writeToBuildFile(TestFile.EMPTY_ANDROID_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue(33)
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_MAJOR_VERSION_ONLY_EXPECTED)
  }

  @Test
  fun testUpdateCompileSdkAllValuesVersionWithOldApi() {
    writeToBuildFile(TestFile.EMPTY_ANDROID_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue("android-33.1-ext18")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_WITH_MINOR_VERSION_AND_EXTENSION_EXPECTED)
  }

  @Test
  fun testUpdateCompileSdkWithMinorVersionWithOldApi() {
    writeToBuildFile(TestFile.EMPTY_ANDROID_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue("android-33.1")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_WITH_MINOR_VERSION_EXPECTED)
  }

  @Test
  fun testUpdateCompileSdkWithExtensionVersionWithOldApi() {
    writeToBuildFile(TestFile.EMPTY_ANDROID_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue("android-33-ext1")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_WITH_EXTENSION_VERSION_EXPECTED)
  }

  @Test
  fun testUpdateCompileSdkWithPreviewWithOldApi() {
    writeToBuildFile(TestFile.EMPTY_ANDROID_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue("Tiramisu")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_WITH_PREVIEW_VERSION_EXPECTED)
  }

  @Test
  fun testUpdateCompileSdkWithAddonWithOldApi() {
    writeToBuildFile(TestFile.EMPTY_ANDROID_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue("vendorName:addonName:1")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_WITH_ADDON_VERSION_EXPECTED)
  }

  @Test
  fun testCreateCompileSdkWithZeroMinorRelease() {
    writeToBuildFile(TestFile.EMPTY_ANDROID_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    val config = compileSdkVersion.toCompileSdkConfig()
    config!!.setReleaseVersion(35,0, null)
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_WITH_ZERO_MINOR_VERSION_EXPECTED)
  }

  @Test
  fun testReadUpdateCompileSdkValuesWithOldApi() {
    writeToBuildFile(TestFile.EMPTY_ANDROID_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()

    compileSdkVersion.setValue("android-33.1")
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("android-33.1")
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.INTEGER_TYPE)).isNull()
    assertThat(compileSdkVersion.toCompileSdkConfig()?.getVersion()).isInstanceOf(CompileSdkReleaseModel::class.java)

    compileSdkVersion.setValue(33)
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(33)
    assertThat(compileSdkVersion.toCompileSdkConfig()?.getVersion()).isInstanceOf(CompileSdkReleaseModel::class.java)

    compileSdkVersion.setValue("Tiramisu")
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("Tiramisu")
    assertThat(compileSdkVersion.toCompileSdkConfig()?.getVersion()).isInstanceOf(CompileSdkPreviewModel::class.java)
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.INTEGER_TYPE)).isNull()

    compileSdkVersion.setValue("vendorName:addonName:1")
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("vendorName:addonName:1")
    assertThat(compileSdkVersion.toCompileSdkConfig()?.getVersion()).isInstanceOf(CompileSdkAddonModel::class.java)
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.INTEGER_TYPE)).isNull()
  }

  @Test
  fun testWriteCompileSdkAfterElement() {
    writeToBuildFile(TestFile.WRITE_RELEASE_BLOCK_AFTER_ELEMENT)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion(android.namespace())
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue("android-33")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.WRITE_RELEASE_BLOCK_AFTER_ELEMENT_EXPECTED)
  }

  @Test
  fun testWriteCompileSdkAfterElementForOldAgp() {
    writeToBuildFile(TestFile.WRITE_RELEASE_BLOCK_AFTER_ELEMENT)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse("8.12.0")

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion(android.namespace())
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue("android-33")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.WRITE_RELEASE_BLOCK_AFTER_ELEMENT_OLD_AGP_EXPECTED)
  }

  @Test
  fun testPickupNotSavedElementForOldApi() {
    writeToBuildFile(TestFile.EMPTY_ANDROID_BLOCK)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse("8.12.0")

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue(33)

    assertThat(android.compileSdkVersion().getValue(GradlePropertyModel.INTEGER_TYPE)).isEqualTo(33)
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    READ_RELEASE_BLOCK("releaseBlock"),
    READ_RELEASE_METHOD("releaseMethod"),
    READ_RELEASE_METHOD_WITH_REFERENCE("releaseMethodWithReference"),
    READ_PREVIEW_METHOD_WITH_REFERENCE("previewMethodWithReference"),
    READ_PREVIEW_METHOD("previewMethod"),
    READ_ADDON_METHOD("addonMethod"),
    EMPTY_ANDROID_BLOCK("emptyAndroidBlock"),
    SET_RELEASE_METHOD_TO_REFERENCE("setReleaseMethodToReference"),
    SET_RELEASE_METHOD_TO_REFERENCE_EXPECTED("setReleaseMethodToReferenceExpected"),
    SET_PREVIEW_METHOD_TO_REFERENCE("setPreviewMethodToReference"),
    SET_PREVIEW_METHOD_TO_REFERENCE_EXPECTED("setPreviewMethodToReferenceExpected"),
    WRITE_RELEASE_BLOCK_AFTER_ELEMENT("releaseBlockAfterElement"),
    CREATE_MAJOR_VERSION_ONLY_EXPECTED("createMajorVersionOnlyExpected"),
    CREATE_WITH_MINOR_VERSION_AND_EXTENSION_EXPECTED("createWithMinorAndExtensionExpected"),
    CREATE_WITH_MINOR_VERSION_EXPECTED("createWithMinorVersionExpected"),
    CREATE_WITH_EXTENSION_VERSION_EXPECTED("createWithExtensionVersionExpected"),
    CREATE_WITH_PREVIEW_VERSION_EXPECTED("createWithPreviewVersionExpected"),
    CREATE_WITH_ADDON_VERSION_EXPECTED("createWithAddonVersionExpected"),
    CREATE_WITH_ZERO_MINOR_VERSION_EXPECTED("createWithZeroNumberVersionExpected"),
    WRITE_RELEASE_BLOCK_AFTER_ELEMENT_EXPECTED("releaseBlockAfterElementExpected"),
    WRITE_RELEASE_BLOCK_AFTER_ELEMENT_OLD_AGP_EXPECTED("releaseBlockAfterElementOldAgpExpected"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/compileSdk/$path", extension)
    }
  }
}