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
package com.android.tools.idea.npw.module.recipes

import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleInstrumentedTestJava
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleInstrumentedTestKt
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestJava
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestKt
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.DEFAULT_CMAKE_VERSION
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf
import java.io.File

fun generateManifest(
  hasApplicationBlock: Boolean = false,
  theme: String = "@style/Theme.App",
  usesFeatureBlock: String = "",
  hasRoundIcon: Boolean = true,
  appCategory: String = "",
  addBackupRules: Boolean = false,
): String {
  val backupBlock =
    renderIf(addBackupRules) {
      """
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules"
    """
    }
  val applicationBlock =
    if (hasApplicationBlock)
      """
    <application
    android:allowBackup="true"
    $backupBlock
    ${renderIf(appCategory.isNotBlank()) { """android:appCategory="$appCategory"""" }}
    android:label="@string/app_name"
    android:icon="@mipmap/ic_launcher"
    ${renderIf(hasRoundIcon) { """android:roundIcon="@mipmap/ic_launcher_round"""" }}
    android:supportsRtl="true"
    android:theme="$theme" />
  """
    else ""

  return """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
    ${renderIf(addBackupRules) { """xmlns:tools="http://schemas.android.com/tools"""" }}
    >
    $usesFeatureBlock
    $applicationBlock
    </manifest>
  """
}

fun proguardConfig(
  // Incubating, see
  // https://google.github.io/android-gradle-dsl/current/com.android.build.gradle.internal.dsl.BuildType.html
  postprocessing: Boolean = false
) =
  if (postprocessing) {
    """
    buildTypes {
        release {
            postprocessing {
                removeUnusedCode false
                removeUnusedResources false
                obfuscate false
                optimizeCode false
                proguardFile 'proguard-rules.pro'
            }
        }
    }
    """
  } else {
    """
    buildTypes {
       release {
           minifyEnabled false
           proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
       }
    }
    """
  }

fun toAndroidFieldVersion(fieldName: String, fieldValue: String, agpVersion: AgpVersion): String {
  val isNewAGP = agpVersion.compareIgnoringQualifiers("7.0.0") >= 0
  val androidVersion = AndroidVersion.fromString(fieldValue)
  // TODO(b/409390818): Include minor version when AGP supports it
  val apiLevelMajor = androidVersion.androidApiLevel.majorVersion

  return when {
    // TODO(b/409631131): replace might not be needed anymore
    isNewAGP && androidVersion.isPreview ->
      "${fieldName}Preview \"${fieldValue.replace("android-", "")}\""
    isNewAGP -> "$fieldName $apiLevelMajor"
    androidVersion.isPreview -> "${fieldName}Version \"$fieldValue\""
    else -> "${fieldName}Version $apiLevelMajor"
  }
}

fun androidConfig(
  agpVersion: AgpVersion,
  buildApiString: String,
  minApi: String,
  targetApi: String,
  useAndroidX: Boolean,
  isLibraryProject: Boolean,
  isDynamicFeature: Boolean,
  explicitApplicationId: Boolean,
  applicationId: String,
  hasTests: Boolean,
  canUseProguard: Boolean,
  addLintOptions: Boolean,
  enableCpp: Boolean,
  cppStandard: CppStandardType,
): String {
  val propertiesBlock =
    if (isDynamicFeature) {
      toAndroidFieldVersion("minSdk", minApi, agpVersion)
    } else {
      """${renderIf(explicitApplicationId) { "applicationId \"${applicationId}\"" }}
    ${toAndroidFieldVersion("minSdk", minApi, agpVersion)}
    ${renderIf(!isLibraryProject) { toAndroidFieldVersion("targetSdk", targetApi, agpVersion) }}
    ${renderIf(!isLibraryProject) { "versionCode 1" }}
    ${renderIf(!isLibraryProject) { "versionName \"1.0\"" }}
    """
    }
  val testsBlock =
    renderIf(hasTests) {
      "testInstrumentationRunner \"${getMaterialComponentName("android.support.test.runner.AndroidJUnitRunner", useAndroidX)}\""
    }
  val proguardConsumerBlock =
    renderIf(canUseProguard && isLibraryProject) { "consumerProguardFiles \"consumer-rules.pro\"" }
  val proguardConfigBlock = renderIf(canUseProguard) { proguardConfig() }
  val lintOptionsBlock =
    renderIf(addLintOptions) {
      """
      lintOptions {
          disable ('AllowBackup', 'GoogleAppIndexingWarning', 'MissingApplicationIcon')
      }
    """
    }

  val cppConfigBlock =
    renderIf(enableCpp) {
      """
      externalNativeBuild {
        cmake {
          cppFlags "${cppStandard.compilerFlag}"
        }
      }
    """
    }

  val cppReferenceBlock =
    renderIf(enableCpp) {
      """
    externalNativeBuild {
      cmake {
        path "src/main/cpp/CMakeLists.txt"
        version "$DEFAULT_CMAKE_VERSION"
      }
    }
    """
    }

  return """
    android {
    namespace '$applicationId'
    ${toAndroidFieldVersion("compileSdk", buildApiString, agpVersion)}

    defaultConfig {
      $propertiesBlock
      $testsBlock
      $proguardConsumerBlock
      $cppConfigBlock
    }

    $proguardConfigBlock
    $lintOptionsBlock
    $cppReferenceBlock
    }
    """
}

private fun resource(path: String) = File("templates/module", path)

fun RecipeExecutor.copyIcons(destination: File, minApi: Int) {
  fun apiSuffix(api: Int) = if (api > minApi) "-v$api" else ""

  fun copyAdaptiveIcons() {
    copy(
      resource("mipmap-anydpi-v26/ic_launcher.xml"),
      destination.resolve("mipmap-anydpi${apiSuffix(26)}/ic_launcher.xml"),
    )
    copy(
      resource("drawable/ic_launcher_background.xml"),
      destination.resolve("drawable/ic_launcher_background.xml"),
    )
    copy(
      resource("drawable-v24/ic_launcher_foreground.xml"),
      destination.resolve("drawable${apiSuffix(24)}/ic_launcher_foreground.xml"),
    )
    copy(
      resource("mipmap-anydpi-v26/ic_launcher_round.xml"),
      destination.resolve("mipmap-anydpi${apiSuffix(26)}/ic_launcher_round.xml"),
    )
  }

  copyMipmapFolder(destination)
  copyMipmapFolder(destination)
  copyAdaptiveIcons()
}

fun RecipeExecutor.copyMipmapFolder(destination: File) {
  copy(resource("mipmap-hdpi"), destination.resolve("mipmap-hdpi"))
  copy(resource("mipmap-mdpi"), destination.resolve("mipmap-mdpi"))
  copy(resource("mipmap-xhdpi"), destination.resolve("mipmap-xhdpi"))
  copy(resource("mipmap-xxhdpi"), destination.resolve("mipmap-xxhdpi"))
  copy(resource("mipmap-xxxhdpi"), destination.resolve("mipmap-xxxhdpi"))
}

fun RecipeExecutor.copyMipmapFile(destination: File, file: String) {
  copy(resource("mipmap-hdpi/$file"), destination.resolve("mipmap-hdpi/$file"))
  copy(resource("mipmap-mdpi/$file"), destination.resolve("mipmap-mdpi/$file"))
  copy(resource("mipmap-xhdpi/$file"), destination.resolve("mipmap-xhdpi/$file"))
  copy(resource("mipmap-xxhdpi/$file"), destination.resolve("mipmap-xxhdpi/$file"))
  copy(resource("mipmap-xxxhdpi/$file"), destination.resolve("mipmap-xxxhdpi/$file"))
}

fun RecipeExecutor.addLocalTests(packageName: String, localTestOut: File, language: Language) {
  val ext = language.extension
  save(
    if (language == Language.Kotlin) exampleUnitTestKt(packageName)
    else exampleUnitTestJava(packageName),
    localTestOut.resolve("ExampleUnitTest.$ext"),
  )
}

fun RecipeExecutor.addInstrumentedTests(
  packageName: String,
  useAndroidX: Boolean,
  isLibraryProject: Boolean,
  instrumentedTestOut: File,
  language: Language,
) {
  val ext = language.extension
  save(
    if (language == Language.Kotlin)
      exampleInstrumentedTestKt(packageName, useAndroidX, isLibraryProject)
    else exampleInstrumentedTestJava(packageName, useAndroidX, isLibraryProject),
    instrumentedTestOut.resolve("ExampleInstrumentedTest.$ext"),
  )
}

/** Plugin block placeholder. Used to introduce an extra space at the bottom of the block. */
fun emptyPluginsBlock() =
  """
plugins {
}
"""

fun basicThemesXml(parent: String, themeName: String = "Theme.App") =
  """
<resources>
    <style name="$themeName" parent="$parent" />
</resources>
"""

fun RecipeExecutor.addTestDependencies() {
  addDependency("junit:junit:4.+", "testImplementation", minRev = "4.13.2")
  addDependency("com.android.support.test:runner:+", "androidTestImplementation")
  addDependency("com.android.support.test.espresso:espresso-core:+", "androidTestImplementation")
}
