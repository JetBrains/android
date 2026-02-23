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
import com.android.tools.idea.wizard.template.RecipeExecutor
import java.io.File

const val keepRules =
  """
# Add project specific R8 rules here.
# AGP will combine all keep rule files in src/main/keepRules to pass to R8
#
# For more details, see
#   https://d.android.com/r/tools/r8/keep-rules

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
 """

const val proguardRules =
  """
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
 """

fun RecipeExecutor.proguardRecipe(projectOut: File, version: AgpVersion, isLibraryProject: Boolean = false) {
  if (version < AgpVersion.parse("9.0.0")) {
    save(proguardRules, projectOut.resolve("proguard-rules.pro"))
  } else {
    val keepRulesFolder = projectOut.resolve("src/main/keepRules")
    keepRulesFolder.mkdirs()
    save(keepRules, keepRulesFolder.resolve("rules.keep"))
  }
  if (isLibraryProject) {
    val aarKeepRulesFolder = projectOut.resolve("src/main/keepRules")
    aarKeepRulesFolder.mkdirs()
    if (version < AgpVersion.parse("9.0.0")) {
      save("", projectOut.resolve("consumer-rules.pro"))
    } else {
      save("", projectOut.resolve("consumer-rules.keep"))
    }
  }
}
