/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.configurations

import com.android.annotations.concurrency.Slow
import com.android.resources.ScreenSize
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device

/** Information about the theming used by [ConfigurationManager]. */
interface ThemeInfoProvider {
  /** If found, returns the application theme name. */
  @get:Slow
  val appThemeName: String?

  val allActivityThemeNames: Set<String>

  /** If found, returns a theme name corresponding the [activityFqcn] activity. */
  @Slow
  fun getThemeNameForActivity(activityFqcn: String): String?

  /** Returns a default theme name. */
  fun getDefaultTheme(renderingTarget: IAndroidTarget?, screenSize: ScreenSize?, device: Device?): String
}