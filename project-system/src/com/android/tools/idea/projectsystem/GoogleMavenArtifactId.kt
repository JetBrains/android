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

/**
 * Enumeration of known artifacts used in Android Studio
 */
enum class GoogleMavenArtifactId(val artifactCoordinate: String, val isSupportLibrary: Boolean) {
  // Layout and view libs
  CONSTRAINT_LAYOUT(SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT, false),
  FLEXBOX_LAYOUT(SdkConstants.FLEXBOX_LAYOUT_LIB_ARTIFACT, false),
  GRID_LAYOUT(SdkConstants.GRID_LAYOUT_LIB_ARTIFACT, true),
  CARD_VIEW(SdkConstants.CARD_VIEW_LIB_ARTIFACT, true),
  RECYCLER_VIEW(SdkConstants.RECYCLER_VIEW_LIB_ARTIFACT, true),

  // General support
  SUPPORT_LIB(SdkConstants.SUPPORT_LIB_ARTIFACT, true),
  DESIGN_LIB(SdkConstants.DESIGN_LIB_ARTIFACT, true),
  APPCOMPAT_V7(SdkConstants.APPCOMPAT_LIB_ARTIFACT, true),
  ANNOTATIONS_LIB(SdkConstants.ANNOTATIONS_LIB_ARTIFACT, true),

  // Databinding
  DATA_BINDING_LIB(SdkConstants.DATA_BINDING_LIB_ARTIFACT, false),
  DATA_BINDING_BASELIB(SdkConstants.DATA_BINDING_BASELIB_ARTIFACT, false),
  DATA_BINDING_ANNOTATION_PROCESSOR(SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT, false),
  DATA_BINDING_ADAPTERS(SdkConstants.DATA_BINDING_ADAPTER_LIB_ARTIFACT, false),

  // Misc.
  MAPS(SdkConstants.MAPS_ARTIFACT, false),
  ADS(SdkConstants.ADS_ARTIFACT, false),
  LEANBACK_V17(SdkConstants.LEANBACK_V17_ARTIFACT, true);

  companion object {
    @JvmStatic
    fun fromArtifactGradleCoordinate(coordinate: String): GoogleMavenArtifactId? {
      return GoogleMavenArtifactId.values().find { it.artifactCoordinate == coordinate }
    }
  }
}
