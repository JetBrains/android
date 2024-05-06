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
package com.android.tools.idea.debuggers.coroutine

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider
import com.intellij.xdebugger.settings.DebuggerSettingsCategory

class CoroutineDebuggerConfigurableProviderTest : LightPlatformTestCase() {

  override fun tearDown() {
    // reset to default value
    CoroutineDebuggerSettings.setCoroutineDebuggerEnabled(true)

    super.tearDown()
  }

  fun testProviderIsRegistered() {
    val provider = getProvider()
    assertNotNull(provider)
  }

  fun testProviderIsDisabledWithFeatureFlag() = runWithFlagState(false) {
    val provider = getProvider()
    val configurables = provider.getConfigurables(DebuggerSettingsCategory.GENERAL)

    assertEmpty(configurables)
  }

  fun testProviderReturnsCoroutineConfigurable() = runWithFlagState(true) {
    val provider = getProvider()
    val configurables = provider.getConfigurables(DebuggerSettingsCategory.GENERAL)

    assertSize(1, configurables)

    val configurable = configurables.first()
    val component = configurable.createComponent()!!

    assertEquals("Coroutine Debugger", configurable.displayName)
    assertEquals(1, component.componentCount)
    assertTrue(component.getComponent (0) is JBCheckBox)
  }

  fun testConfigurableCanResetAndApplySettings() = runWithFlagState(true) {
    val provider = getProvider()
    val configurable = provider.getConfigurables(DebuggerSettingsCategory.GENERAL).first()
    val coroutineDebuggerEnabledCheckBox = configurable.createComponent()!!.getComponent(0) as JBCheckBox

    assertTrue(CoroutineDebuggerSettings.isCoroutineDebuggerEnabled())
    assertFalse(coroutineDebuggerEnabledCheckBox.isSelected)

    configurable.reset()

    assertTrue(CoroutineDebuggerSettings.isCoroutineDebuggerEnabled())
    assertTrue(coroutineDebuggerEnabledCheckBox.isSelected)

    coroutineDebuggerEnabledCheckBox.isSelected = false
    assertTrue(configurable.isModified)
    assertTrue(CoroutineDebuggerSettings.isCoroutineDebuggerEnabled())

    configurable.apply()
    assertFalse(configurable.isModified)
    assertFalse(CoroutineDebuggerSettings.isCoroutineDebuggerEnabled())
  }

  private fun getProvider(): CoroutineDebuggerConfigurableProvider {
    return DebuggerConfigurableProvider.EXTENSION_POINT.extensionList.filterIsInstance<CoroutineDebuggerConfigurableProvider>().first()
  }
}