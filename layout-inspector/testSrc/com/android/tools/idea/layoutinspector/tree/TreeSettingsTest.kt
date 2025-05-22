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

import com.android.testutils.MockitoCleanerRule
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InspectorTreeSettingsTest {

  @get:Rule val cleaner = MockitoCleanerRule()

  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val projectRule = ProjectRule()

  private val client: InspectorClient = mock()
  private val capabilities = mutableSetOf<Capability>()
  private var isConnected = false
  private lateinit var inspector: LayoutInspector
  private lateinit var settings: TreeSettings

  @Before
  fun before() {
    val application = ApplicationManager.getApplication()
    application.registerServiceInstance(
      PropertiesComponent::class.java,
      PropertiesComponentMock(),
      disposableRule.disposable,
    )
    settings = InspectorTreeSettings { client }
    val model = InspectorModel(projectRule.project, AndroidCoroutineScope(projectRule.disposable))
    val mockLauncher = mock<InspectorClientLauncher>()
    whenever(mockLauncher.activeClient).thenAnswer { DisconnectedClient }
    inspector =
      LayoutInspector(
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
        mockLauncher,
        model,
        mock(),
        settings,
        FakeRenderSettings(),
        MoreExecutors.directExecutor(),
      )
    doAnswer { capabilities }.whenever(client).capabilities
    doAnswer { isConnected }.whenever(client).isConnected
  }

  @Test
  fun testHideSystemNodes() {
    testFlag(DEFAULT_HIDE_SYSTEM_NODES, KEY_HIDE_SYSTEM_NODES, Capability.SUPPORTS_SYSTEM_NODES) {
      settings.hideSystemNodes
    }
  }

  @Test
  fun testComposeAsCallStack() {
    testFlag(DEFAULT_COMPOSE_AS_CALLSTACK, KEY_COMPOSE_AS_CALLSTACK, null) {
      settings.composeAsCallstack
    }
  }

  @Test
  fun testHighlightSemantics() {
    assertThat(settings.highlightSemantics).isFalse()
    settings.highlightSemantics = true
    assertThat(settings.highlightSemantics).isTrue()
    settings.highlightSemantics = false
    assertThat(settings.highlightSemantics).isFalse()
  }

  @Test
  fun testSupportLines() {
    testFlag(DEFAULT_SUPPORT_LINES, KEY_SUPPORT_LINES, null) { settings.supportLines }
  }

  @Test
  fun testRecompositionsEnabledByDefault() {
    assertThat(DEFAULT_RECOMPOSITIONS).isTrue()
  }

  @Test
  fun testShowRecompositions() {
    testFlag(
      DEFAULT_RECOMPOSITIONS,
      KEY_RECOMPOSITIONS,
      Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS,
    ) {
      settings.showRecompositions
    }

    capabilities.add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)
    settings.showRecompositions = true
    assertThat(settings.showRecompositions).isTrue()
  }

  private fun testFlag(
    defaultValue: Boolean,
    key: String,
    controllingCapability: Capability?,
    flag: () -> Boolean,
  ) {
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
    } else {
      // All flags (except supportLines) should return false if connected and their controlling
      // capability is off:
      isConnected = true
      assertThat(flag()).named("Connected without $controllingCapability (default): $key").isFalse()
      properties.setValue(key, !defaultValue, defaultValue)
      assertThat(flag())
        .named("Connected without $controllingCapability (opposite): $key")
        .isFalse()
      properties.unsetValue(key)

      // All flags should return their actual value if connected and their controlling capability is
      // on:
      capabilities.add(controllingCapability)
      assertThat(flag())
        .named("Connected with $controllingCapability (default): $key")
        .isEqualTo(defaultValue)
      properties.setValue(key, !defaultValue, defaultValue)
      assertThat(flag())
        .named("Connected with $controllingCapability (opposite): $key")
        .isEqualTo(!defaultValue)
      properties.unsetValue(key)
    }
  }
}

class EditorTreeSettingsTest {
  @Test
  fun testSettings() {
    val client: InspectorClient = mock()
    whenever(client.capabilities).thenReturn(setOf(Capability.SUPPORTS_SYSTEM_NODES))
    val settings1 = EditorTreeSettings(client.capabilities)
    assertThat(settings1.composeAsCallstack).isEqualTo(DEFAULT_COMPOSE_AS_CALLSTACK)
    assertThat(settings1.hideSystemNodes).isEqualTo(DEFAULT_HIDE_SYSTEM_NODES)
    assertThat(settings1.highlightSemantics).isEqualTo(DEFAULT_HIGHLIGHT_SEMANTICS)
    assertThat(settings1.supportLines).isEqualTo(DEFAULT_SUPPORT_LINES)

    settings1.hideSystemNodes = !DEFAULT_HIDE_SYSTEM_NODES
    settings1.supportLines = !DEFAULT_SUPPORT_LINES
    assertThat(settings1.supportLines).isEqualTo(!DEFAULT_SUPPORT_LINES)
    assertThat(settings1.hideSystemNodes).isEqualTo(!DEFAULT_HIDE_SYSTEM_NODES)

    whenever(client.capabilities).thenReturn(setOf())
    val settings2 = EditorTreeSettings(client.capabilities)
    assertThat(settings2.hideSystemNodes).isEqualTo(false)
    assertThat(settings2.supportLines).isEqualTo(DEFAULT_SUPPORT_LINES)
  }
}
