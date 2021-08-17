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

import com.android.flags.junit.SetFlagRule
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.util.PropertiesComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doAnswer

class TreeSettingsTest {

  @get:Rule
  val appRule = ApplicationRule()

  @get:Rule
  val flagRule = SetFlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS, true)

  private val client: InspectorClient = mock()
  private val capabilities = mutableSetOf<Capability>()
  private var isConnected = false
  private lateinit var inspector: LayoutInspector
  private lateinit var settings: TreeSettings

  @Before
  fun before() {
    appRule.testApplication.registerService(PropertiesComponent::class.java, PropertiesComponentMock())
    settings = TreeSettingsImpl { client }
    inspector = LayoutInspector(mock(), mock(), mock(), settings, MoreExecutors.directExecutor())
    doAnswer { capabilities }.`when`(client).capabilities
    doAnswer { isConnected }.`when`(client).isConnected
  }

  @Test
  fun testHideSystemNodes() {
    testFlag(DEFAULT_HIDE_SYSTEM_NODES, KEY_HIDE_SYSTEM_NODES, Capability.SUPPORTS_SYSTEM_NODES) { settings.hideSystemNodes }
  }

  @Test
  fun testComposeAsCallStack() {
    testFlag(DEFAULT_COMPOSE_AS_CALLSTACK, KEY_COMPOSE_AS_CALLSTACK, null) { settings.composeAsCallstack }
  }

  @Test
  fun testMergedSemanticsTree() {
    testFlag(DEFAULT_MERGED_SEMANTICS_TREE, KEY_MERGED_SEMANTICS_TREE, Capability.SUPPORTS_SEMANTICS) { settings.mergedSemanticsTree }
  }

  @Test
  fun testUnmergedSemanticsTree() {
    testFlag(DEFAULT_UNMERGED_SEMANTICS_TREE, KEY_UNMERGED_SEMANTICS_TREE, Capability.SUPPORTS_SEMANTICS) { settings.unmergedSemanticsTree }
  }

  @Test
  fun testSupportLines() {
    testFlag(DEFAULT_SUPPORT_LINES, KEY_SUPPORT_LINES, null) { settings.supportLines }
  }

  private fun testFlag(defaultValue: Boolean, key: String, controllingCapability: Capability?, flag: () -> Boolean) {
    capabilities.clear()

    // All flags should return their actual value in disconnected state:
    isConnected = false
    assertThat(flag()).named("Disconnected Default value of: $key").isEqualTo(defaultValue)
    val properties = PropertiesComponent.getInstance()
    properties.setValue(key, !defaultValue, defaultValue)
    assertThat(flag()).named("Disconnected opposite value of: $key").isEqualTo(!defaultValue)
    properties.unsetValue(key)

    if (controllingCapability == null) {
      // Flags without a controlling capability should return their actual value when connected:
      isConnected = true
      assertThat(flag()).named("Connected (default): $key").isEqualTo(defaultValue)
      properties.setValue(key, !defaultValue, defaultValue)
      assertThat(flag()).named("Connected (opposite): $key").isEqualTo(!defaultValue)
      properties.unsetValue(key)
    }
    else {
      // All flags (except supportLines) should return false if connected and their controlling capability is off:
      isConnected = true
      assertThat(flag()).named("Connected without $controllingCapability (default): $key").isFalse()
      properties.setValue(key, !defaultValue, defaultValue)
      assertThat(flag()).named("Connected without $controllingCapability (opposite): $key").isFalse()
      properties.unsetValue(key)

      // All flags should return their actual value if connected and their controlling capability is on:
      capabilities.add(controllingCapability)
      assertThat(flag()).named("Connected with $controllingCapability (default): $key").isEqualTo(defaultValue)
      properties.setValue(key, !defaultValue, defaultValue)
      assertThat(flag()).named("Connected with $controllingCapability (opposite): $key").isEqualTo(!defaultValue)
      properties.unsetValue(key)
    }
  }
}
