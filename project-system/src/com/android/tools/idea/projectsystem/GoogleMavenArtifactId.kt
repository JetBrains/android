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

import com.android.SdkConstants
import com.android.ide.common.repository.GradleCoordinate

/**
 * Enumeration of known artifacts used in Android Studio
 */
enum class GoogleMavenArtifactId(val mavenGroupId: String, val mavenArtifactId: String) {

  // Platform support libraries
  SUPPORT_ANNOTATIONS("com.android.support", "support-annotations"),
  SUPPORT_V4("com.android.support", "support-v4"),
  SUPPORT_V13("com.android.support", "support-v13"),
  APP_COMPAT_V7("com.android.support", "appcompat-v7"),
  SUPPORT_VECTOR_DRAWABLE("com.android.support", "support-vector-drawable"),
  DESIGN("com.android.support", "design"),
  GRID_LAYOUT_V7("com.android.support", "gridlayout-v7"),
  MEDIA_ROUTER_V7("com.android.support", "mediarouter-v7"),
  CARDVIEW_V7("com.android.support", "cardview-v7"),
  PALETTE_V7("com.android.support", "palette-v7"),
  LEANBACK_V17("com.android.support", "leanback-v17"),
  RECYCLERVIEW_V7("com.android.support", "recyclerview-v7"),
  EXIF_INTERFACE("com.android.support", "exifinterface"),

  // Misc. layouts
  CONSTRAINT_LAYOUT("com.android.support.constraint", "constraint-layout"),
  FLEXBOX_LAYOUT("com.google.android", "flexbox"),

  // Testing
  TEST_RUNNER("com.android.support.test", "runner"),
  ESPRESSO_CORE("com.android.support.test.espresso", "espresso-core"),
  ESPRESSO_CONTRIB("com.android.support.test.espresso", "espresso-contrib"),

  // Data binding
  DATA_BINDING_LIB("com.android.databinding", "library"),
  DATA_BINDING_BASELIB("com.android.databinding", "baseLibrary"),
  DATA_BINDING_ANNOTATION_PROCESSOR("com.android.databinding", "compiler"),
  DATA_BINDING_ADAPTERS("com.android.databinding", "adapters"),

  // Google repo
  PLAY_SERVICES("com.google.android.gms", "play-services"),
  PLAY_SERVICES_ADS("com.google.android.gms", "play-services-ads"),
  PLAY_SERVICES_WEARABLE("com.google.android.gms", "play-services-wearable"),
  PLAY_SERVICES_MAPS("com.google.android.gms", "play-services-maps"),
  WEARABLE("com.google.android.support", "wearable"),
  ;

  fun getCoordinate(revision: String): GradleCoordinate =
    GradleCoordinate(mavenGroupId, mavenArtifactId, GradleCoordinate.StringComponent(revision))

  override fun toString(): String = "$mavenGroupId:$mavenArtifactId"

  companion object {
    fun find(groupId: String, artifactId: String): GoogleMavenArtifactId? =
        values().asSequence().find { it.mavenGroupId == groupId && it.mavenArtifactId == artifactId }

    fun forCoordinate(coordinate: GradleCoordinate): GoogleMavenArtifactId? {
      val groupId = coordinate.groupId ?: return null
      val artifactId = coordinate.artifactId ?: return null

      return find(groupId, artifactId)
    }
  }
}
