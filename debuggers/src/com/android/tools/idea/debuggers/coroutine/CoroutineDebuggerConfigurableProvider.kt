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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.SimpleConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider
import com.intellij.xdebugger.settings.DebuggerSettingsCategory
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

private const val ID = "coroutineDebuggerSettingsConfigurable"
private const val DISPLAY_NAME = "Coroutine Debugger"

/**
 * Class used by Intellij to get preferences for the Debugger section.
 */
class CoroutineDebuggerConfigurableProvider : DebuggerConfigurableProvider() {
  override fun getConfigurables(category: DebuggerSettingsCategory): List<Configurable> {
    if (!FlagController.isCoroutineDebuggerEnabled) {
      return emptyList()
    }

    if (category != DebuggerSettingsCategory.GENERAL) {
      return emptyList()
    }

    return listOf(
      SimpleConfigurable.create(ID, DISPLAY_NAME, CoroutineDebuggerConfigurableUi::class.java) { CoroutineDebuggerSettings }
    )
  }
}

/**
 * Creates and manages the UI shown in the Intellij preferences for the debugger section.
 */
private class CoroutineDebuggerConfigurableUi : ConfigurableUi<CoroutineDebuggerSettings> {
  private val coroutineDebuggerEnabledText = "Enable coroutine debugger"
  private val coroutineDebuggerEnabledCheckbox = JBCheckBox(coroutineDebuggerEnabledText)

  private val component: JComponent

  init {
    component = JPanel(BorderLayout())
    component.add(coroutineDebuggerEnabledCheckbox, BorderLayout.LINE_START)
  }

  override fun reset(settings: CoroutineDebuggerSettings) {
    coroutineDebuggerEnabledCheckbox.isSelected = settings.isCoroutineDebuggerEnabled()
  }

  override fun isModified(settings: CoroutineDebuggerSettings): Boolean {
    return coroutineDebuggerEnabledCheckbox.isSelected != settings.isCoroutineDebuggerEnabled();
  }

  override fun apply(settings: CoroutineDebuggerSettings) {
    settings.setCoroutineDebuggerEnabled(coroutineDebuggerEnabledCheckbox.isSelected)
  }

  override fun getComponent(): JComponent {
    return component
  }
}

/**
 * Object used to access and set the preferences for the coroutine debugger.
 */
object CoroutineDebuggerSettings {
  private const val COROUTINE_DEBUGGER_ENABLED_DEFAULT = true
  private const val COROUTINE_DEBUGGER_ENABLED = "coroutine.debugger.enabled"

  fun setCoroutineDebuggerEnabled(enabled: Boolean) {
    PropertiesComponent.getInstance().setValue(COROUTINE_DEBUGGER_ENABLED, enabled, COROUTINE_DEBUGGER_ENABLED_DEFAULT)
  }

  fun isCoroutineDebuggerEnabled(): Boolean {
    return PropertiesComponent.getInstance().getBoolean(COROUTINE_DEBUGGER_ENABLED, COROUTINE_DEBUGGER_ENABLED_DEFAULT)
  }

  fun reset() {
    PropertiesComponent.getInstance().unsetValue(COROUTINE_DEBUGGER_ENABLED)
  }
}