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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class VisualizationUtilTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testAddCustomCategory() {
    val settings = VisualizationToolSettings.getInstance()
    val customConfigurations = mutableMapOf<String, CustomConfigurationSet>()

    settings.globalState.customConfigurationSets = customConfigurations
    VisualizationUtil.setCustomConfigurationSet("Test", CustomConfigurationSet("My Set", emptyList()))

    // Check the values are same after getting another instance.
    val anotherSettings = VisualizationToolSettings.getInstance()
    assertEquals(customConfigurations, anotherSettings.globalState.customConfigurationSets)
    assertEquals(CustomConfigurationSet("My Set", emptyList()), anotherSettings.globalState.customConfigurationSets["Test"])

    // Add another set with a custom attribute
    VisualizationUtil.setCustomConfigurationSet("Test 2", CustomConfigurationSet("My Set2", listOf(CustomConfigurationAttribute())))
    assertEquals(customConfigurations, anotherSettings.globalState.customConfigurationSets)
    // Check first one is still kept.
    assertEquals(CustomConfigurationSet("My Set", emptyList()), anotherSettings.globalState.customConfigurationSets["Test"])
    assertEquals(CustomConfigurationSet("My Set2", listOf(CustomConfigurationAttribute())),
                 anotherSettings.globalState.customConfigurationSets["Test 2"])

    // Check the first one is removed and second one is still there.
    VisualizationUtil.setCustomConfigurationSet("Test", null)
    assertNull(anotherSettings.globalState.customConfigurationSets["Test"])
    assertEquals(CustomConfigurationSet("My Set2", listOf(CustomConfigurationAttribute())),
                 anotherSettings.globalState.customConfigurationSets["Test 2"])
  }
}
