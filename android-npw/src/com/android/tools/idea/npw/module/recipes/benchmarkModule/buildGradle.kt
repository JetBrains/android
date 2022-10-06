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

package com.android.tools.idea.npw.module.recipes.benchmarkModule

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.npw.module.recipes.androidModule.gradleToKtsIfKts
import com.android.tools.idea.npw.module.recipes.emptyPluginsBlock
import com.android.tools.idea.npw.module.recipes.toAndroidFieldVersion
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.renderIf

fun buildGradle(
  packageName: String,
  buildApiString: String,
  minApi: String,
  targetApiString: String,
  language: Language,
  gradlePluginVersion: GradlePluginVersion,
  useGradleKts: Boolean
): String {
  val kotlinOptionsBlock = renderIf(language == Language.Kotlin) {
    """
   kotlinOptions {
      jvmTarget = "1.8"
   }
  """
  }

  val isNewAGP = AgpVersion.parse(gradlePluginVersion).compareIgnoringQualifiers("3.6.0") >= 0

  val testBuildTypeBlock = renderIf(isNewAGP) { """testBuildType = "release"""" }

  val releaseBlock = renderIf(isNewAGP) {
    """

    release {
      isDefault = true
    }
    """
  }

  return """
${emptyPluginsBlock()}

android {
    namespace '$packageName'
    ${toAndroidFieldVersion("compileSdk", buildApiString, gradlePluginVersion)}

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    $kotlinOptionsBlock

    defaultConfig {
        ${toAndroidFieldVersion("minSdk", minApi, gradlePluginVersion)}
        ${toAndroidFieldVersion("targetSdk", targetApiString, gradlePluginVersion)}

        testInstrumentationRunner 'androidx.benchmark.junit4.AndroidBenchmarkRunner'
    }

    $testBuildTypeBlock
    buildTypes {
        debug {
            // Since debuggable can"t be modified by gradle for library modules,
            // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
            minifyEnabled true
            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-proguard-rules.pro"
        }
        $releaseBlock
    }
}

dependencies {
    // Add your dependencies here. Note that you cannot benchmark code
    // in an app module this way - you will need to move any code you
    // want to benchmark to a library module:
    // https://developer.android.com/studio/projects/android-library#Convert

}
""".gradleToKtsIfKts(useGradleKts)
}
