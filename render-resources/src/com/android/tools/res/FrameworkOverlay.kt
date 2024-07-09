/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.res

/**
 * All available framework resource overlays.
 *
 * The Android framework uses resource overlays to customize the values of some
 * of its resources at runtime. Several overlays can be applied at the same time,
 * but not all combinations make sense are some overlays override the same resources
 * as others.
 */
enum class FrameworkOverlay(val overlayName: String) {
  AVOID_APPS_IN_CUTOUT("AvoidAppsInCutoutOverlay"),
  CUTOUT_CORNER("DisplayCutoutEmulationCornerOverlay"),
  CUTOUT_DOUBLE("DisplayCutoutEmulationDoubleOverlay"),
  CUTOUT_HOLE("DisplayCutoutEmulationHoleOverlay"),
  CUTOUT_NARROW("DisplayCutoutEmulationNarrowOverlay"),
  CUTOUT_NONE("NoCutoutOverlay"),
  CUTOUT_TALL("DisplayCutoutEmulationTallOverlay"),
  CUTOUT_WATERFALL("DisplayCutoutEmulationWaterfallOverlay"),
  CUTOUT_WIDE("DisplayCutoutEmulationWideOverlay"),
  FONT_NOTO_SERIF_SOURCE("FontNotoSerifSourceOverlay"),
  NAV_2_BUTTONS("NavigationBarMode2ButtonOverlay"),
  NAV_3_BUTTONS("NavigationBarMode3ButtonOverlay"),
  NAV_GESTURE("NavigationBarModeGesturalOverlay"),
  NAV_GESTURE_EXTRA_WIDE("NavigationBarModeGesturalOverlayExtraWideBack"),
  NAV_GESTURE_NARROW("NavigationBarModeGesturalOverlayNarrowBack"),
  NAV_GESTURE_WIDE("NavigationBarModeGesturalOverlayWideBack"),
  NOTES_ROLE_ENABLED("NotesRoleEnabledOverlay"),
  TRANSPARENT_NAV_BAR("TransparentNavigationBarOverlay"),

  PIXEL_2_XL("pixel_2_xl"),
  PIXEL_3("pixel_3"),
  PIXEL_3A("pixel_3a"),
  PIXEL_3A_XL("pixel_3a_xl"),
  PIXEL_3_XL("pixel_3_xl"),
  PIXEL_4("pixel_4"),
  PIXEL_4A("pixel_4a"),
  PIXEL_4_XL("pixel_4_xl"),
  PIXEL_5("pixel_5"),
  PIXEL_6("pixel_6"),
  PIXEL_6A("pixel_6a"),
  PIXEL_6_PRO("pixel_6_pro"),
  PIXEL_7("pixel_7"),
  PIXEL_7A("pixel_7a"),
  PIXEL_7_PRO("pixel_7_pro"),
  PIXEL_8("pixel_8"),
  PIXEL_8_PRO("pixel_8_pro"),
  PIXEL_FOLD("pixel_fold"),
}