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
  NOTES_ROLE_ENABLED("NotesRoleEnabledOverlay"),
  TRANSPARENT_NAV_BAR("TransparentNavigationBarOverlay"),

  PIXEL_6("pixel_6"),
  PIXEL_6A("pixel_6a"),
  PIXEL_6_PRO("pixel_6_pro"),
  PIXEL_7("pixel_7"),
  PIXEL_7A("pixel_7a"),
  PIXEL_7_PRO("pixel_7_pro"),
  PIXEL_8("pixel_8"),
  PIXEL_8A("pixel_8a"),
  PIXEL_8_PRO("pixel_8_pro"),
  PIXEL_9("pixel_9"),
  PIXEL_9_PRO("pixel_9_pro"),
  PIXEL_9_PRO_FOLD("pixel_9_pro_fold"),
  PIXEL_9_PRO_XL("pixel_9_pro_xl"),
  PIXEL_FOLD("pixel_fold"),
  PIXEL_TABLET("pixel_tablet"),
}