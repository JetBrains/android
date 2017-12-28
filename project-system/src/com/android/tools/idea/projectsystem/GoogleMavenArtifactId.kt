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
  SUPPORT_V4("com.android.support", "support-v4", true),
  SUPPORT_V13("com.android.support", "support-v13", true),
  APP_COMPAT_V7("com.android.support", "appcompat-v7", true),
  SUPPORT_VECTOR_DRAWABLE("com.android.support", "support-vector-drawable", true),
  DESIGN("com.android.support", "design", true),
  GRID_LAYOUT_V7("com.android.support", "gridlayout-v7", true),
  MEDIA_ROUTER_V7("com.android.support", "mediarouter-v7", true),
  CARDVIEW_V7("com.android.support", "cardview-v7", true),
  PALETTE_V7("com.android.support", "palette-v7", true),
  LEANBACK_V17("com.android.support", "leanback-v17", true),
  RECYCLERVIEW_V7("com.android.support", "recyclerview-v7", true),
  EXIF_INTERFACE("com.android.support", "exifinterface", true),

  // Misc. layouts
  CONSTRAINT_LAYOUT("com.android.support.constraint", "constraint-layout", false),
  FLEXBOX_LAYOUT("com.google.android", "flexbox", false),

  // Navigation
  NAVIGATION("android.arch.navigation", "runtime", false),

  // Testing
  TEST_RUNNER("com.android.support.test", "runner", false),
  ESPRESSO_CORE("com.android.support.test.espresso", "espresso-core", false),
  ESPRESSO_CONTRIB("com.android.support.test.espresso", "espresso-contrib", false),

  // Data binding
  DATA_BINDING_LIB("com.android.databinding", "library", false),
  DATA_BINDING_BASELIB("com.android.databinding", "baseLibrary", false),
  DATA_BINDING_ANNOTATION_PROCESSOR("com.android.databinding", "compiler", false),
  DATA_BINDING_ADAPTERS("com.android.databinding", "adapters", false),

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
