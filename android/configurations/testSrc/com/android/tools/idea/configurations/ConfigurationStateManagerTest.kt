/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.intellij.ide.trustedProjects.TrustedProjects
import org.jetbrains.android.AndroidTestCase

class ConfigurationStateManagerTest : AndroidTestCase() {
  fun testProjectStateSavingAndLoading() {
    // Kotlin automatically provides 'project' as a property from AndroidTestCase
    assertNotNull(project)

    val manager = StudioConfigurationStateManager.get(project) as StudioConfigurationStateManager
    assertNotNull(manager)
    assertSame(manager, StudioConfigurationStateManager.get(project))

    // Converted Java getter/setters to idiomatic Kotlin property access syntax
    manager.projectState.locale = "en-rUS"
    assertEquals("en-rUS", manager.projectState.locale)

    // requireNotNull acts as both an assertion and safely casts it to a non-null type
    val firstState = requireNotNull(manager.state)
    manager.projectState = ConfigurationProjectState()

    manager.projectState.locale = "de"
    assertEquals("de", manager.projectState.locale)

    val secondState = requireNotNull(manager.state)

    // Verify that loading the first state successfully restores "en-rUS"
    manager.loadState(firstState)
    assertEquals("en-rUS", manager.projectState.locale)
    // Verify that loading the second state successfully restores "de"
    manager.loadState(secondState)
    assertEquals("de", manager.projectState.locale)
    assertTrue(manager.projectState.isPickTarget)
    manager.projectState.isPickTarget = false
    assertFalse(manager.projectState.isPickTarget)
  }

  fun testFileStateSavingAndLoading() {
    assertNotNull(project)

    val manager = StudioConfigurationStateManager.get(project)
    assertNotNull(manager)
    assertSame(manager, StudioConfigurationStateManager.get(project))

    val file = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout.xml")
    val configState1 = ConfigurationFileState()
    configState1.theme = "@style/Theme.Holo.Light"
    configState1.deviceState = "port"
    manager.setConfigurationState(file, configState1)

    val state1 = requireNotNull(manager.state)

    val configState2 = ConfigurationFileState()
    configState2.theme = "@style/Theme.Dialog"
    configState2.deviceState = "land"
    manager.setConfigurationState(file, configState2)

    val state2 = requireNotNull(manager.state)

    val retrievedState1 = requireNotNull(manager.getConfigurationState(file))
    assertEquals("@style/Theme.Dialog", retrievedState1.theme)
    assertEquals("land", retrievedState1.deviceState)

    manager.loadState(state1)

    val retrievedState2 = requireNotNull(manager.getConfigurationState(file))
    assertEquals("@style/Theme.Holo.Light", retrievedState2.theme)
    assertEquals("port", retrievedState2.deviceState)

    assertNotSame(retrievedState1, configState1)

    manager.loadState(state2)

    val retrievedState3 = requireNotNull(manager.getConfigurationState(file))
    assertEquals("@style/Theme.Dialog", retrievedState3.theme)
    assertEquals("land", retrievedState3.deviceState)
  }

  fun testLoadStateUntrustedProject() {
    assertNotNull(project)
    val manager = StudioConfigurationStateManager.get(project)
    val file = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout.xml")
    val configState = ConfigurationFileState()
    configState.theme = "@style/Theme.Holo.Light"
    manager.setConfigurationState(file, configState)

    val state = requireNotNull(manager.state)

    // Reset manager state
    manager.loadState(ConfigurationStateManager.State())
    assertNull(manager.getConfigurationState(file))

    val wasTrusted = TrustedProjects.isProjectTrusted(project)
    try {
      TrustedProjects.setProjectTrusted(project, false)
      manager.loadState(state)
      // State should NOT be loaded because project is untrusted
      assertNull(manager.getConfigurationState(file))

      TrustedProjects.setProjectTrusted(project, true)
      manager.loadState(state)
      // State SHOULD be loaded because project is trusted
      assertNotNull(manager.getConfigurationState(file))
    } finally {
      TrustedProjects.setProjectTrusted(project, wasTrusted)
    }
  }

  fun testLoadStateWithNullProjectState() {
    assertNotNull(project)
    val manager = StudioConfigurationStateManager.get(project) as StudioConfigurationStateManager
    assertNotNull(manager)

    val state = ConfigurationStateManager.State()
    assertNull(state.projectState)

    manager.loadState(state)
    assertNotNull(manager.projectState)
    assertNotNull(manager.projectState.deviceIds)
  }
}
