/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.SdkConstants.CONSTRAINT_LAYOUT_LIB_GROUP_ID
import com.android.ide.common.repository.GradleCoordinate

/**
 * Enumeration of known artifacts used in Android Studio
 */
enum class GoogleMavenArtifactId(val mavenGroupId: String, val mavenArtifactId: String, val isPlatformSupportLibrary: Boolean) {

  // Platform support libraries
  SUPPORT_ANNOTATIONS("com.android.support", "support-annotations", true),
  ANDROIDX_SUPPORT_ANNOTATIONS("androidx.annotation", "annotation", true),
  SUPPORT_V4("com.android.support", "support-v4", true),
  ANDROIDX_SUPPORT_V4("androidx.legacy", "legacy-support-v4", true),
  SUPPORT_V13("com.android.support", "support-v13", true),
  ANDROIDX_SUPPORT_V13("androidx.legacy", "legacy-support-v13", true),
  APP_COMPAT_V7("com.android.support", "appcompat-v7", true),
  ANDROIDX_APP_COMPAT_V7("androidx.appcompat", "appcompat", true),
  SUPPORT_VECTOR_DRAWABLE("com.android.support", "support-vector-drawable", true),
  ANDROIDX_SUPPORT_VECTOR_DRAWABLE("androidx.vectordrawable", "vectordrawable", true),
  DESIGN("com.android.support", "design", true),
  ANDROIDX_DESIGN("com.google.android.material", "material", true),
  GRID_LAYOUT_V7("com.android.support", "gridlayout-v7", true),
  ANDROIDX_GRID_LAYOUT_V7("androidx.gridlayout", "gridlayout", true),
  MEDIA_ROUTER_V7("com.android.support", "mediarouter-v7", true),
  ANDROIDX_MEDIA_ROUTER_V7("androidx.mediarouter", "mediarouter", true),
  CARDVIEW_V7("com.android.support", "cardview-v7", true),
  ANDROIDX_CARDVIEW_V7("androidx.cardview", "cardview", true),
  PALETTE_V7("com.android.support", "palette-v7", true),
  ANDROIDX_PALETTE_V7("androidx.palette", "palette", true),
  LEANBACK_V17("com.android.support", "leanback-v17", true),
  ANDROIDX_LEANBACK_V17("androidx.leanback", "leanback", true),
  RECYCLERVIEW_V7("com.android.support", "recyclerview-v7", true),
  ANDROIDX_RECYCLERVIEW_V7("androidx.recyclerview", "recyclerview", true),
  EXIF_INTERFACE("com.android.support", "exifinterface", true),
  ANDROIDX_EXIF_INTERFACE("androidx.exifinterface", "exifinterface", true),

  // Misc. layouts
  CONSTRAINT_LAYOUT("com.android.support.constraint", "constraint-layout", false),
  ANDROIDX_CONSTRAINT_LAYOUT("androidx.constraintlayout", "constraintlayout", false),
  ANDROIDX_CONSTRAINT_LAYOUT_COMPOSE("androidx.constraintlayout", "constraintlayout-compose", false),
  FLEXBOX_LAYOUT("com.google.android", "flexbox", false),

  // Navigation
  NAVIGATION_FRAGMENT("android.arch.navigation", "navigation-fragment", false),
  ANDROIDX_NAVIGATION_FRAGMENT("androidx.navigation", "navigation-fragment", false),
  NAVIGATION_UI("android.arch.navigation", "navigation-ui", false),
  ANDROIDX_NAVIGATION_UI("androidx.navigation", "navigation-ui", false),
  NAVIGATION_FRAGMENT_KTX("android.arch.navigation", "navigation-fragment-ktx", false),
  ANDROIDX_NAVIGATION_FRAGMENT_KTX("androidx.navigation", "navigation-fragment-ktx", false),
  NAVIGATION_UI_KTX("android.arch.navigation", "navigation-ui-ktx", false),
  ANDROIDX_NAVIGATION_UI_KTX("androidx.navigation", "navigation-ui-ktx", false),
  // This is currently only used in tests
  NAVIGATION("androidx.navigation", "navigation-runtime", false),
  ANDROIDX_NAVIGATION_DYNAMIC_FEATURES_FRAGMENT("androidx.navigation", "navigation-dynamic-features-fragment", false),
  ANDROIDX_NAVIGATION_COMMON("androidx.navigation", "navigation-common", false),

  // Testing
  TEST_RUNNER("com.android.support.test", "runner", false),
  ANDROIDX_TEST_RUNNER("androidx.test.espresso", "test-runner", false),
  ESPRESSO_CORE("com.android.support.test.espresso", "espresso-core", false),
  ANDROIDX_ESPRESSO_CORE("androidx.test.espresso", "espresso-core", false),
  ESPRESSO_CONTRIB("com.android.support.test.espresso", "espresso-contrib", false),
  ANDROIDX_ESPRESSO_CONTRIB("androidx.test.espresso", "espresso-contrib", false),
  TEST_RULES("com.android.support.test", "rules", false),
  ANDROIDX_TEST_RULES("androidx.test", "rules", false),
  ANDROIDX_TEST_EXT_JUNIT("androidx.test.ext", "junit", false),

  // Data binding
  DATA_BINDING_LIB("com.android.databinding", "library", false),
  ANDROIDX_DATA_BINDING_LIB("androidx.databinding", "databinding-runtime", false),
  DATA_BINDING_BASELIB("com.android.databinding", "baseLibrary", false),
  ANDROIDX_DATA_BINDING_BASELIB("androidx.databinding", "databinding-common", false),
  DATA_BINDING_ANNOTATION_PROCESSOR("com.android.databinding", "compiler", false),
  ANDROIDX_DATA_BINDING_ANNOTATION_PROCESSOR("androidx.databinding", "databinding-compiler", false),
  DATA_BINDING_ADAPTERS("com.android.databinding", "adapters", false),
  ANDROIDX_DATA_BINDING_ADAPTERS("androidx.databinding", "databinding-adapters", false),

  // App Inspection supported libraries
  ANDROIDX_WORK_RUNTIME("androidx.work", "work-runtime", false),

  // Google repo
  PLAY_SERVICES("com.google.android.gms", "play-services", false),
  PLAY_SERVICES_ADS("com.google.android.gms", "play-services-ads", false),
  PLAY_SERVICES_WEARABLE("com.google.android.gms", "play-services-wearable", false),
  PLAY_SERVICES_MAPS("com.google.android.gms", "play-services-maps", false),
  WEARABLE("com.google.android.support", "wearable", false),

  // Compose
  COMPOSE_RUNTIME("androidx.compose.runtime", "runtime", false),
  COMPOSE_TOOLING("androidx.compose.ui", "ui-tooling", false),
  COMPOSE_TOOLING_PREVIEW("androidx.compose.ui", "ui-tooling-preview", false),
  COMPOSE_UI("androidx.compose.ui", "ui", false),
  JETBRAINS_COMPOSE_TOOLING_PREVIEW("org.jetbrains.compose", "ui-tooling-preview", false),

  // Kotlin
  KOTLIN_STDLIB("org.jetbrains.kotlin", "kotlin-stdlib", false),
  KOTLIN_REFLECT("org.jetbrains.kotlin", "kotlin-reflect", false),
  ;

  fun getCoordinate(revision: String): GradleCoordinate =
      GradleCoordinate(mavenGroupId, mavenArtifactId, revision)

  fun isAndroidxLibrary(): Boolean = mavenGroupId.startsWith("androidx.")

  fun isAndroidxPlatformLibrary(): Boolean = isPlatformSupportLibrary && isAndroidxLibrary()

  fun hasAndroidxEquivalent(): Boolean {
    // All the platform support libraries have an androidx version
    if (isPlatformSupportLibrary) {
      return true
    }
    // Return true to the rest of groups that also have androidx version
    when(mavenGroupId) {
      CONSTRAINT_LAYOUT_LIB_GROUP_ID,
        "com.android.support.test",
        "com.android.support.test.espresso",
        "com.android.databinding",
        "com.google.android.gms",
        "com.google.android.support" -> return true
    }
    // The rest of the libraries do not have an equivalent Androidx library
    return false
  }

  override fun toString(): String = "$mavenGroupId:$mavenArtifactId"

  companion object {
    @JvmStatic fun find(groupId: String, artifactId: String): GoogleMavenArtifactId? =
        values().asSequence().find { it.mavenGroupId == groupId && it.mavenArtifactId == artifactId }

    @JvmStatic fun forCoordinate(coordinate: GradleCoordinate): GoogleMavenArtifactId? {
      val groupId = coordinate.groupId ?: return null
      val artifactId = coordinate.artifactId ?: return null

      return find(groupId, artifactId)
    }
  }
}
