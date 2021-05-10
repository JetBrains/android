/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource.data

/**
 * Data received from device that corresponds with the framework class of the same name.
 *
 * See also: https://developer.android.com/reference/android/content/res/Configuration
 */
class Configuration(
  val countryCode: Int = 0,
  val networkCode: Int = 0,
  val locale: Locale = Locale(),
  val screenLayout: Int = 0,
  val colorMode: Int = 0,
  val touchScreen: Int = 0,
  val keyboard: Int = 0,
  val keyboardHidden: Int = 0,
  val hardKeyboardHidden: Int = 0,
  val navigation: Int = 0,
  val navigationHidden: Int = 0,
  val uiMode: Int = 0,
  val smallestScreenWidth: Int = 0,
  val density: Int = 0,
  val orientation: Int = 0,
  val screenWidth: Int = 0,
  val screenHeight: Int = 0,
)