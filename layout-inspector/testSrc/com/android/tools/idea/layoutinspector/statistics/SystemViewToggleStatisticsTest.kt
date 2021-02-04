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
package com.android.tools.idea.layoutinspector.statistics

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSystemNode
import com.intellij.ide.util.PropertiesComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SystemViewToggleStatisticsTest {
  @get:Rule
  val appRule = ApplicationRule()

  @Before
  fun before() {
    appRule.testApplication.registerService(PropertiesComponent::class.java, PropertiesComponentMock())
  }

  @Test
  fun testStart() {
    val systemViewToggle = SystemViewToggleStatistics()
    systemViewToggle.selectionMade()
    systemViewToggle.selectionMade()
    systemViewToggle.selectionMade()
    TreeSettings.hideSystemNodes = false
    systemViewToggle.selectionMade()
    systemViewToggle.start()
    val data = DynamicLayoutInspectorSystemNode.newBuilder()
    systemViewToggle.save(data)
    assertThat(data.clicksWithHiddenSystemViews).isEqualTo(0)
    assertThat(data.clicksWithVisibleSystemViews).isEqualTo(0)
  }

  @Test
  fun testToggleBackAndForth() {
    val systemViewToggle = SystemViewToggleStatistics()
    systemViewToggle.start()

    TreeSettings.hideSystemNodes = true
    systemViewToggle.selectionMade()
    systemViewToggle.selectionMade()
    systemViewToggle.selectionMade()
    TreeSettings.hideSystemNodes = false
    systemViewToggle.selectionMade()
    val data = DynamicLayoutInspectorSystemNode.newBuilder()
    systemViewToggle.save(data)
    assertThat(data.clicksWithHiddenSystemViews).isEqualTo(3)
    assertThat(data.clicksWithVisibleSystemViews).isEqualTo(1)
  }
}
