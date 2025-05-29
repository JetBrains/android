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

import com.android.tools.idea.wizard.template.ApiVersion
import com.android.tools.idea.wizard.template.MaterialColor.BLACK
import com.android.tools.idea.wizard.template.MaterialColor.PURPLE_500
import com.android.tools.idea.wizard.template.MaterialColor.PURPLE_700
import com.android.tools.idea.wizard.template.MaterialColor.TEAL_200
import com.android.tools.idea.wizard.template.MaterialColor.TEAL_700
import com.android.tools.idea.wizard.template.MaterialColor.WHITE

const val DARK_ACTION_BAR_MATERIAL_COMPONENTS = "Theme.MaterialComponents.DayNight.DarkActionBar"
const val DARK_ACTION_BAR_APPCOMPAT = "Theme.AppCompat.Light.DarkActionBar"

fun androidModuleThemesMaterial3(themeName: String) =
  // When the contents are modified, need to modify
  // com.android.tools.idea.wizard.template.impl.activities.common.generateMaterial3Themes
  """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="Base.${themeName}" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- Customize your light theme here. -->
    <!-- <item name="colorPrimary">@color/my_light_primary</item> -->
  </style>

  <style name="$themeName" parent="Base.${themeName}" />
</resources>"""

fun androidModuleThemes(useAndroidX: Boolean, minSdk: ApiVersion, themeName: String = "Theme.App") =
  if (useAndroidX)
    """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="$themeName" parent="$DARK_ACTION_BAR_MATERIAL_COMPONENTS">
      <!-- Primary brand color. -->
      <item name="colorPrimary">@color/${PURPLE_500.colorName}</item>
      <item name="colorPrimaryVariant">@color/${PURPLE_700.colorName}</item>
      <item name="colorOnPrimary">@color/${WHITE.colorName}</item>
      <!-- Secondary brand color. -->
      <item name="colorSecondary">@color/${TEAL_200.colorName}</item>
      <item name="colorSecondaryVariant">@color/${TEAL_700.colorName}</item>
      <item name="colorOnSecondary">@color/${BLACK.colorName}</item>
      <!-- Status bar color. -->
      <item name="android:statusBarColor"${if (minSdk.api < 21) " tools:targetApi=\"21\"" else ""}>?attr/colorPrimaryVariant</item>
      <!-- Customize your theme here. -->
  </style>
</resources>"""
  else
    """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="$themeName" parent="$DARK_ACTION_BAR_APPCOMPAT">
      <!-- Primary brand color. -->
      <item name="colorPrimary">@color/${PURPLE_500.colorName}</item>
      <item name="colorPrimaryDark">@color/${PURPLE_700.colorName}</item>
      <item name="colorAccent">@color/${TEAL_200.colorName}</item>
      <!-- Customize your theme here. -->
  </style>
</resources>"""
