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
@file:JvmName("NavTestUtil")
package com.android.tools.idea.naveditor

import com.android.tools.idea.templates.IdeGoogleMavenRepository

val navEditorAarPaths: Map<String, String> by lazy {
  val supportFragmentVersion = IdeGoogleMavenRepository.findVersion("com.android.support", "support-fragment")

  val navigationFragmentVersion = IdeGoogleMavenRepository.findVersion(
    "android.arch.navigation",
    "navigation-fragment",
    allowPreview = true
  )
  val runtimeVersion = IdeGoogleMavenRepository.findVersion(
    "android.arch.navigation",
    "navigation-runtime",
    allowPreview = true
  )
  val commonVersion = IdeGoogleMavenRepository.findVersion(
    "android.arch.navigation",
    "navigation-common",
    allowPreview = true
  )

  val repo = "../../prebuilts/tools/common/m2/repository"

  mapOf(
    "$repo/android/arch/navigation/navigation-runtime/$runtimeVersion/navigation-runtime-$runtimeVersion.aar" to
      "android.arch.navigation:navigation-runtime:$runtimeVersion",
    "$repo/android/arch/navigation/navigation-common/$commonVersion/navigation-common-$commonVersion.aar" to
      "android.arch.navigation:navigation-common:$runtimeVersion",
    "$repo/android/arch/navigation/navigation-fragment/$navigationFragmentVersion/navigation-fragment-$navigationFragmentVersion.aar" to
      "android.arch.navigation:navigation-fragment:$runtimeVersion",
    "$repo/com/android/support/support-fragment/$supportFragmentVersion/support-fragment-$supportFragmentVersion.aar" to
      "com.android.support:support-fragment:$runtimeVersion"
  )
}

