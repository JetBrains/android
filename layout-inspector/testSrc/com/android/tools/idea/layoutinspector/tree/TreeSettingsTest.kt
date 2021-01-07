/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TreeSettingsTest {

  @get:Rule
  val appRule = ApplicationRule()

  @Before
  fun before() {
    appRule.testApplication.registerService(PropertiesComponent::class.java, PropertiesComponentMock())
  }

  @Test
  fun testDefaultValue() {
    assertThat(TreeSettings.hideSystemNodes).isTrue()
  }

  @Test
  fun testReadSavedValue() {
    val properties = PropertiesComponent.getInstance()
    properties.setValue(KEY_HIDE_SYSTEM_NODES, false, DEFAULT_HIDE_SYSTEM_NODES)
    assertThat(TreeSettings.hideSystemNodes).isFalse()

    properties.setValue(KEY_HIDE_SYSTEM_NODES, true, DEFAULT_HIDE_SYSTEM_NODES)
    assertThat(TreeSettings.hideSystemNodes).isTrue()
  }

  @Test
  fun testSaveValue() {
    val properties = PropertiesComponent.getInstance()
    TreeSettings.hideSystemNodes = false
    assertThat(properties.getBoolean(KEY_HIDE_SYSTEM_NODES, DEFAULT_HIDE_SYSTEM_NODES)).isFalse()

    TreeSettings.hideSystemNodes = true
    assertThat(properties.getBoolean(KEY_HIDE_SYSTEM_NODES, DEFAULT_HIDE_SYSTEM_NODES)).isTrue()
    assertThat(properties.getValue(KEY_HIDE_SYSTEM_NODES)).isNull()
  }
}