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
  FLEXBOX_LAYOUT("com.google.android", "flexbox", false),

  // Navigation
  NAVIGATION("androidx.navigation", "runtime", false),
  NAVIGATION_FRAGMENT("androidx.navigation", "fragment", false),

  // Testing
  TEST_RUNNER("com.android.support.test", "runner", false),
  ANDROIDX_TEST_RUNNER("androidx.test.espresso", "test-runner", false),
  ESPRESSO_CORE("com.android.support.test.espresso", "espresso-core", false),
  ANDROIDX_ESPRESSO_CORE("androidx.test.espresso", "espresso-core", false),
  ESPRESSO_CONTRIB("com.android.support.test.espresso", "espresso-contrib", false),
  ANDROIDX_ESPRESSO_CONTRIB("androidx.test.espresso", "espresso-contrib", false),

  // Data binding
  DATA_BINDING_LIB("com.android.databinding", "library", false),
  ANDROIDX_DATA_BINDING_LIB("androidx.databinding", "databinding-runtime", false),
  DATA_BINDING_BASELIB("com.android.databinding", "baseLibrary", false),
  ANDROIDX_DATA_BINDING_BASELIB("androidx.databinding", "databinding-common", false),
  DATA_BINDING_ANNOTATION_PROCESSOR("com.android.databinding", "compiler", false),
  ANDROIDX_DATA_BINDING_ANNOTATION_PROCESSOR("androidx.databinding", "databinding-compiler", false),
  DATA_BINDING_ADAPTERS("com.android.databinding", "adapters", false),
  ANDROIDX_DATA_BINDING_ADAPTERS("androidx.databinding", "databinding-adapters", false),

  // Google repo
  PLAY_SERVICES("com.google.android.gms", "play-services", false),
  PLAY_SERVICES_ADS("com.google.android.gms", "play-services-ads", false),
  PLAY_SERVICES_WEARABLE("com.google.android.gms", "play-services-wearable", false),
  PLAY_SERVICES_MAPS("com.google.android.gms", "play-services-maps", false),
  WEARABLE("com.google.android.support", "wearable", false),
  ;

  fun getCoordinate(revision: String): GradleCoordinate =
      GradleCoordinate(mavenGroupId, mavenArtifactId, GradleCoordinate.StringComponent(revision))

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
