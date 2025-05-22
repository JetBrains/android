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
@file:JvmName("TestRepositories")

package com.android.tools.idea.projectsystem

import com.android.ide.common.repository.GoogleMavenArtifactId.*

/**
 * A collection of repos for dependencies used during testing.
 */
@JvmField
val PLATFORM_SUPPORT_LIBS =
  // This list isn't particularly principled, but instead reflects the state of GoogleMavenArtifactId at the point that
  // that class stopped keeping track of whether (by some unknown metric) the library was a platform support library.
  // This list should probably be viewed as a tasteful set of available libraries for testing dependency management in
  // both original and androidx projects.
  listOf(
    SUPPORT_ANNOTATIONS,
    ANDROIDX_SUPPORT_ANNOTATIONS,
    SUPPORT_V4,
    ANDROIDX_SUPPORT_V4,
    SUPPORT_V13,
    ANDROIDX_SUPPORT_V13,
    APP_COMPAT_V7,
    ANDROIDX_APP_COMPAT_V7,
    SUPPORT_VECTOR_DRAWABLE,
    ANDROIDX_SUPPORT_VECTOR_DRAWABLE,
    DESIGN,
    ANDROIDX_DESIGN,
    GRID_LAYOUT_V7,
    ANDROIDX_GRID_LAYOUT_V7,
    MEDIA_ROUTER_V7,
    ANDROIDX_MEDIA_ROUTER_V7,
    CARDVIEW_V7,
    ANDROIDX_CARDVIEW_V7,
    PALETTE_V7,
    ANDROIDX_PALETTE_V7,
    LEANBACK_V17,
    ANDROIDX_LEANBACK_V17,
    RECYCLERVIEW_V7,
    ANDROIDX_RECYCLERVIEW_V7,
    EXIF_INTERFACE,
    ANDROIDX_EXIF_INTERFACE,
    ANDROIDX_PREFERENCE,
  ).map { it.getCoordinate("+") }
@JvmField
val NON_PLATFORM_SUPPORT_LAYOUT_LIBS = listOf(CONSTRAINT_LAYOUT, ANDROIDX_CONSTRAINT_LAYOUT, FLEXBOX_LAYOUT)
  .map { it.getCoordinate("+") }

@JvmField
val GOOGLE_PLAY_SERVICES = listOf(PLAY_SERVICES, PLAY_SERVICES_ADS, PLAY_SERVICES_WEARABLE, PLAY_SERVICES_MAPS)
  .map { it.getCoordinate("+") }
