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
package com.android.tools.idea.configurations

import com.android.tools.idea.npw.ThemeHelper
import org.jetbrains.android.AndroidTestCase

class ThemeHelperTest : AndroidTestCase() {
  fun testThemeExists() {
    val layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml")
    val configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layoutFile)
    assertTrue(ThemeHelper.themeExists (configuration, "@android:style/Theme.DeviceDefault"))
  }

  fun testNoThemeExists() {
    val layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml")
    val configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layoutFile)
    assertFalse(ThemeHelper.themeExists(configuration, "@NoExistingTheme"))
  }

  fun testThemeWithNoAtSymbol() {
    val layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml")
    val configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layoutFile)
    assertFalse(ThemeHelper.themeExists (configuration, "android:style/Theme.DeviceDefault"))
  }

  fun testHasActionBar() {
    val layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml")
    val configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layoutFile)
    assertTrue(ThemeHelper.hasActionBar(configuration, "@android:style/Theme.DeviceDefault")!!)
  }
}
