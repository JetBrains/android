/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.androidModule

import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.AndroidMajorVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.npw.module.recipes.androidConfig
import com.android.tools.idea.npw.module.recipes.emptyPluginsBlock
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.renderIf

fun buildGradle(
  agpVersion: AgpVersion,
  isKts: Boolean,
  isLibraryProject: Boolean,
  isDynamicFeature: Boolean,
  /** The application ID; also used for the namespace. */
  applicationId: String,
  buildApi: AndroidVersion,
  minApi: AndroidMajorVersion,
  targetApi: AndroidMajorVersion,
  useAndroidX: Boolean,
  isCompose: Boolean = false,
  baseFeatureName: String = "base",
  hasTests: Boolean = true,
  addLintOptions: Boolean = false,
  enableCpp: Boolean = false,
  cppStandard: CppStandardType = CppStandardType.`Toolchain Default`,
  useVersionCatalog: Boolean,
): String {
  val androidConfigBlock =
    androidConfig(
      agpVersion = agpVersion,
      buildApi = buildApi,
      minApi = minApi,
      targetApi = targetApi,
      useAndroidX = useAndroidX,
      isLibraryProject = isLibraryProject,
      isDynamicFeature = isDynamicFeature,
      explicitApplicationId = !isLibraryProject,
      applicationId = applicationId,
      hasTests = hasTests,
      canUseProguard = true,
      addLintOptions = addLintOptions,
      enableCpp = enableCpp,
      cppStandard = cppStandard,
    )

  if (isDynamicFeature) {
    return """
$androidConfigBlock

dependencies {
    implementation project("${baseFeatureName}")
}
"""
      .gradleToKtsIfKts(isKts)
  }

  val composeDependenciesBlock =
    renderIf(isCompose) { "kotlinPlugin \"androidx.compose:compose-compiler:+\"" }

  val dependenciesBlock =
    """
  dependencies {
    $composeDependenciesBlock
  }
  """

  val allBlocks =
    """
    ${emptyPluginsBlock()}
    $androidConfigBlock
    $dependenciesBlock
    """

  return allBlocks.gradleToKtsIfKts(isKts)
}

private fun String.toKtsFunction(funcName: String): String =
  if (this.contains("$funcName ")) {
    this.replace("$funcName ", "$funcName(") + ")"
  } else {
    this
  }

private fun String.toKtsProperty(funcName: String): String =
  this.replace("$funcName ", "$funcName = ")

internal fun String.gradleToKtsIfKts(isKts: Boolean): String =
  if (isKts) {
    split("\n").joinToString("\n") {
      it
        .replace("'", "\"")
        .toKtsProperty("namespace")
        .toKtsFunction("compileSdkVersion")
        .toKtsProperty("compileSdk")
        .toKtsProperty("compileSdkPreview")
        .toKtsProperty("buildToolsVersion")
        .toKtsProperty("applicationId")
        .toKtsFunction("minSdkVersion")
        .toKtsProperty("minSdk")
        .toKtsProperty("minSdkPreview")
        .toKtsFunction("targetSdkVersion")
        .toKtsProperty("targetSdk")
        .toKtsProperty("targetSdkPreview")
        .toKtsProperty("versionCode")
        .toKtsProperty("versionName")
        .toKtsProperty("testInstrumentationRunner")
        .toKtsProperty("minifyEnabled")
        .toKtsFunction("proguardFiles")
        .toKtsFunction("consumerProguardFiles")
        .toKtsFunction(
          "implementation"
        ) // For dynamic app: implementation project(":app") -> implementation(project(":app"))
        .replace("minifyEnabled", "isMinifyEnabled")
        .replace("debuggable", "isDebuggable")
        // The followings are for externalNativeBuild
        .toKtsFunction("cppFlags")
        .toKtsFunction("path")
        .toKtsProperty("version")
    }
  } else {
    this
  }
