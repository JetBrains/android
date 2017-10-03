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
enum class GoogleMavenArtifactId(val artifactCoordinate: String) {
  // Layout and view libs
  CONSTRAINT_LAYOUT(SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT),
  FLEXBOX_LAYOUT(SdkConstants.FLEXBOX_LAYOUT_LIB_ARTIFACT),
  GRID_LAYOUT(SdkConstants.GRID_LAYOUT_LIB_ARTIFACT),
  CARD_VIEW(SdkConstants.CARD_VIEW_LIB_ARTIFACT),
  RECYCLER_VIEW(SdkConstants.RECYCLER_VIEW_LIB_ARTIFACT),

  // General support
  SUPPORT_LIB(SdkConstants.SUPPORT_LIB_ARTIFACT),
  DESIGN_LIB(SdkConstants.DESIGN_LIB_ARTIFACT),
  APPCOMPAT_V7(SdkConstants.APPCOMPAT_LIB_ARTIFACT),
  ANNOTATIONS_LIB(SdkConstants.ANNOTATIONS_LIB_ARTIFACT),
  LEANBACK_V17(SdkConstants.LEANBACK_V17_ARTIFACT),

  // Databinding
  DATA_BINDING_LIB(SdkConstants.DATA_BINDING_LIB_ARTIFACT),
  DATA_BINDING_BASELIB(SdkConstants.DATA_BINDING_BASELIB_ARTIFACT),
  DATA_BINDING_ANNOTATION_PROCESSOR(SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT),
  DATA_BINDING_ADAPTERS(SdkConstants.DATA_BINDING_ADAPTER_LIB_ARTIFACT),

  // Misc.
  MAPS(SdkConstants.MAPS_ARTIFACT),
  ADS(SdkConstants.ADS_ARTIFACT),
}
