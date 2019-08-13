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
package com.android.tools.idea.npw.platform

import icons.AndroidIcons.Wizards
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.Icon

/**
 * Representations of navigation types.
 */
enum class NavigationType(val typeName: String, val icon: Icon?, val details: String) {
  NONE("None", null, ""),
  NAVIGATION_DRAWER("Navigation Drawer", Wizards.NavigationDrawer, message(
                                              "android.wizard.activity.navigation.navigation_drawer.details")),
  BOTTOM_NAVIGATION("Bottom Navigation", Wizards.BottomNavigation, message("android.wizard.activity.navigation.bottom_navigation.details")),
  TABS("Tabs", Wizards.NavigationTabs, message("android.wizard.activity.navigation.tabs.details"));

  override fun toString() = typeName
}

val navigationTypeValuesExceptNone = NavigationType.values().filter { it != NavigationType.NONE }
