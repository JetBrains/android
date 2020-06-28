/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.androidModule.res.values

import com.android.tools.idea.wizard.template.MaterialColor.*

const val DARK_ACTION_BAR_MATERIAL_COMPONENTS = "Theme.MaterialComponents.DayNight.DarkActionBar"
const val DARK_ACTION_BAR_APPCOMPAT = "Theme.AppCompat.Light.DarkActionBar"

fun androidModuleThemes(useAndroidX: Boolean, themeName: String = "Theme.App") =
  if (useAndroidX)
    """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="${themeName}" parent="$DARK_ACTION_BAR_MATERIAL_COMPONENTS">
      <!-- Primary brand color. -->
      <item name="colorPrimary">@color/${PURPLE_500.colorName}</item>
      <item name="colorPrimaryDark">@color/${PURPLE_700.colorName}</item>
      <item name="colorOnPrimary">@color/${WHITE.colorName}</item>
      <!-- Secondary brand color. -->
      <item name="colorSecondary">@color/${TEAL_200.colorName}</item>
      <item name="colorSecondaryVariant">@color/${TEAL_700.colorName}</item>
      <item name="colorOnSecondary">@color/${BLACK.colorName}</item>
      <!-- Status bar color. -->
      <item name="android:statusBarColor" tools:targetApi="l">?attr/colorPrimaryVariant</item>
      <!-- Customize your theme here. -->
  </style>
</resources>"""
  else
    """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="${themeName}" parent="$DARK_ACTION_BAR_APPCOMPAT">
      <!-- Primary brand color. -->
      <item name="colorPrimary">@color/${PURPLE_500.colorName}</item>
      <item name="colorPrimaryDark">@color/${PURPLE_700.colorName}</item>
      <item name="colorAccent">@color/${TEAL_200.colorName}</item>
      <!-- Customize your theme here. -->
  </style>
</resources>"""
