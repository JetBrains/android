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
enum class GoogleMavenArtifactId(val commonName: String, val artifactString: String) {
  // Layout and view libs
  CONSTRAINT_LAYOUT("constraint layout", SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT),
  FLEXBOX_LAYOUT("flexbox layout", SdkConstants.FLEXBOX_LAYOUT_LIB_ARTIFACT),
  GRID_LAYOUT("grid layout", SdkConstants.GRID_LAYOUT_LIB_ARTIFACT),
  CARD_VIEW("card view", SdkConstants.CARD_VIEW_LIB_ARTIFACT),
  RECYCLER_VIEW("recycler view", SdkConstants.RECYCLER_VIEW_LIB_ARTIFACT),

  // General support
  SUPPORT_LIB("support library", SdkConstants.SUPPORT_LIB_ARTIFACT),
  DESIGN_LIB("design library", SdkConstants.DESIGN_LIB_ARTIFACT),
  APPCOMPAT_V7("appcompat v7", SdkConstants.APPCOMPAT_LIB_ARTIFACT),
  ANNOTATIONS_LIB("annotations library", SdkConstants.ANNOTATIONS_LIB_ARTIFACT),

  // Databinding
  DATA_BINDING_LIB("databinding library", SdkConstants.DATA_BINDING_LIB_ARTIFACT),
  DATA_BINDING_BASELIB("databinding base library", SdkConstants.DATA_BINDING_BASELIB_ARTIFACT),
  DATA_BINDING_ANNOTATION_PROCESSOR("databinding annotation processor library", SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT),
  DATA_BINDING_ADAPTERS("databinding adapters library", SdkConstants.DATA_BINDING_ADAPTER_LIB_ARTIFACT),

  // Misc.
  MAPS("play services maps", SdkConstants.MAPS_ARTIFACT),
  ADS("play services ads", SdkConstants.ADS_ARTIFACT),
  LEANBACK_V17("support leanback v17", SdkConstants.LEANBACK_V17_ARTIFACT);

  companion object {
    @JvmStatic
    fun getIdByArtifactString(string: String): GoogleMavenArtifactId? {
      return GoogleMavenArtifactId.values().find { it.artifactString == string }
    }

    @JvmStatic
    fun getIdByCommonName(commonName: String): GoogleMavenArtifactId? {
      return GoogleMavenArtifactId.values().find { it.commonName == commonName }
    }
  }
}
