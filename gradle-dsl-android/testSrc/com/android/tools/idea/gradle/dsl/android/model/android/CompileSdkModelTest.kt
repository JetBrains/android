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
package com.android.tools.idea.gradle.dsl.android.model.android

import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.android.model.AndroidGradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.android.api.android.CompileSdkAddonModel
import com.android.tools.idea.gradle.dsl.android.api.android.CompileSdkPreviewModel
import com.android.tools.idea.gradle.dsl.android.api.android.CompileSdkPropertyModel
import com.android.tools.idea.gradle.dsl.android.api.android.CompileSdkReleaseModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion
import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.jetbrains.annotations.SystemDependent
import org.junit.After
import org.junit.Before
import org.junit.Test

class CompileSdkModelTest : AndroidGradleFileModelTestCase() {

  @Before
  override fun before() {
    DeclarativeIdeSupport.override(true)
    DeclarativeStudioSupport.override(true)
    super.before()
  }

  @After
  fun onAfter() {
    DeclarativeIdeSupport.clearOverride()
    DeclarativeStudioSupport.clearOverride()
  }

  @Test
  fun testReadCompileSdkVersionBlock() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.READ_RELEASE_BLOCK)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.READ_RELEASE_METHOD)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.READ_RELEASE_METHOD_WITH_REFERENCE)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.SET_RELEASE_METHOD_TO_REFERENCE)

    val android = buildModel.android()
    assertNotNull(android)

    android.compileSdkVersion().setValue(ReferenceTo(buildModel.ext().findProperty("sdkVersion")))
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_RELEASE_METHOD_TO_REFERENCE_EXPECTED)
  }

  @Test
  fun testReadVariableInCompileSdkVersionPreviewMethod() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.READ_PREVIEW_METHOD_WITH_REFERENCE)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("android-Tiramisu")
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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.SET_PREVIEW_METHOD_TO_REFERENCE)

    val android = buildModel.android()
    assertNotNull(android)

    android.compileSdkVersion().setValue(ReferenceTo(buildModel.ext().findProperty("sdkVersion")))
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_PREVIEW_METHOD_TO_REFERENCE_EXPECTED)
  }

  @Test
  fun testReadCompileSdkVersionPreviewMethod() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.READ_PREVIEW_METHOD)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    assertThat(compileSdkVersion.getRawValue(GradlePropertyModel.OBJECT_TYPE)).isEqualTo("android-Tiramisu")
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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.READ_ADDON_METHOD)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    val config = compileSdkVersion.toCompileSdkConfig()
    config!!.setReleaseVersion(35, 0, null)
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_WITH_ZERO_MINOR_VERSION_EXPECTED)
  }

  @Test
  fun testCompileSdkVersionToString() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()

    assertThat(compileSdkVersion).isNotNull()
    assertThat(compileSdkVersion.toString()).contains("Version=null")
    val config = compileSdkVersion.toCompileSdkConfig()
    config!!.setReleaseVersion(35, 0, null)
    assertThat(compileSdkVersion.toString()).contains("Version=android-35")
  }

  @Test
  fun testCompileSdkValueType() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()

    assertThat(compileSdkVersion).isNotNull()

    val value = compileSdkVersion.getRawValue(GradlePropertyModel.OBJECT_TYPE)?.toString()
    assertThat(value).isNull()
    // for null value - type is NONE
    assertThat(compileSdkVersion.valueType).isEqualTo(ValueType.NONE)

    val config = compileSdkVersion.toCompileSdkConfig()
    config!!.setReleaseVersion(35, 0, null)
    val newValue = compileSdkVersion.getRawValue(GradlePropertyModel.OBJECT_TYPE)?.toString()
    assertThat(newValue).isNotNull()
    // for non null value - it's CUSTOM
    assertThat(compileSdkVersion.valueType).isEqualTo(ValueType.CUSTOM)
  }

  @Test
  fun testReadUpdateCompileSdkValuesWithOldApi() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

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
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("android-Tiramisu")
    assertThat(compileSdkVersion.toCompileSdkConfig()?.getVersion()).isInstanceOf(CompileSdkPreviewModel::class.java)
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.INTEGER_TYPE)).isNull()

    compileSdkVersion.setValue("vendorName:addonName:1")
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.STRING_TYPE)).isEqualTo("vendorName:addonName:1")
    assertThat(compileSdkVersion.toCompileSdkConfig()?.getVersion()).isInstanceOf(CompileSdkAddonModel::class.java)
    assertThat(compileSdkVersion.getValue(GradlePropertyModel.INTEGER_TYPE)).isNull()
  }

  @Test
  fun testWriteCompileSdkAfterElement() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.WRITE_RELEASE_BLOCK_AFTER_ELEMENT)

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
    isIrrelevantForDeclarative("Not supported in Declarative")

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
    isIrrelevantForDeclarative("Not supported in Declarative")

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

  @Test
  fun testSetCompileSdkPreviewWithExistingCompileSdkRelease() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    writeToBuildFile(TestFile.READ_RELEASE_BLOCK)
    val buildModel = gradleBuildModel

    val android = buildModel.android()
    assertNotNull(android)

    android.compileSdkVersion().setValue("android-Tiramisu")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.READ_PREVIEW_METHOD)
  }

  @Test
  fun testSetCompileSdkReleaseWithExistingCompileSdkPreview() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    writeToBuildFile(TestFile.READ_PREVIEW_METHOD)
    val buildModel = gradleBuildModel

    val android = buildModel.android()
    assertNotNull(android)

    android.compileSdkVersion().setValue("android-33.1-ext18")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.READ_RELEASE_BLOCK)
  }

  @Test
  fun testWriteCompileSdkForDeclarative() {
    isIrrelevantForGroovy("Declarative only test")
    isIrrelevantForKotlinScript("Declarative only test")

    writeToBuildFile("androidApp {\n}")
    val buildModel = gradleDeclarativeBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue(33)
    applyChanges(buildModel)

    val content = loadBuildFile()
    assertThat(content).contains("compileSdk = 33")
    assertThat(content).doesNotContain("compileSdk {")
  }

  @Test
  fun testSetBetaVersion() {
    isIrrelevantForDeclarative("Not supported in Declarative")

    val buildModel = initTest(TestFile.EMPTY_ANDROID_BLOCK)

    val android = buildModel.android()
    assertNotNull(android)

    val compileSdkVersion = android.compileSdkVersion()
    assertThat(compileSdkVersion).isNotNull()
    compileSdkVersion.setValue("37.1-beta2")
    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_WITH_BETA_VERSION_EXPECTED)
  }

  private fun initTest(testFileName: TestFileName): GradleBuildModel {
    writeToBuildFile(testFileName)
    val buildModel = gradleBuildModel
    buildModel.context.agpVersion = AndroidGradlePluginVersion.parse(CompileSdkPropertyModel.COMPILE_SDK_BLOCK_VERSION)
    return buildModel
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
    CREATE_WITH_BETA_VERSION_EXPECTED("createWithBetaVersionExpected"),
    WRITE_RELEASE_BLOCK_AFTER_ELEMENT_EXPECTED("releaseBlockAfterElementExpected"),
    WRITE_RELEASE_BLOCK_AFTER_ELEMENT_OLD_AGP_EXPECTED("releaseBlockAfterElementOldAgpExpected");

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/compileSdk/$path", extension)
    }
  }
}
