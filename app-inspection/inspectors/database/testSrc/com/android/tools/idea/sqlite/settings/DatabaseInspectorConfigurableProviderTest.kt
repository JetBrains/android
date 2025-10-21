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
package com.android.tools.idea.sqlite.settings

import com.android.flags.junit.FlagRule
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.TextAccessor
import java.awt.Component
import javax.swing.JCheckBox
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private val EXPERIMENTAL_FLAG = StudioFlags.APP_INSPECTION_USE_EXPERIMENTAL_DATABASE_INSPECTOR
private val ADDITIONAL_DRIVER_FLAG = StudioFlags.APP_INSPECTION_ENABLE_ADDITIONAL_SQL_DRIVER

@RunsInEdt
@RunWith(JUnit4::class)
class DatabaseInspectorConfigurableProviderTest {
  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      FlagRule(EXPERIMENTAL_FLAG, true),
      FlagRule(ADDITIONAL_DRIVER_FLAG, true),
      EdtRule(),
    )

  private val settings by lazy { DatabaseInspectorSettings.getInstance() }
  private val projectSettings by lazy { DatabaseInspectorProjectSettings.getInstance(project) }

  @Test
  fun testConfigurableName() {
    val provider = DatabaseInspectorConfigurableProvider(project)
    val configurable = provider.createConfigurable()

    assertThat(configurable.displayName).isEqualTo("Database Inspector")
  }

  @Test
  fun testConfigurableId() {
    val provider = DatabaseInspectorConfigurableProvider(project)
    val configurable = provider.createConfigurable() as SearchableConfigurable

    assertThat(configurable.id).isEqualTo("database.inspector")
  }

  @Test
  fun initializedFromSettings() {
    settings.isOfflineModeEnabled = false
    settings.isForceOpen = true
    projectSettings.additionalDriverClass = "Driver"
    projectSettings.additionalConnectionClass = "Connection"
    projectSettings.isIgnoreFrameworkApi = true

    val configurable = createConfigurable()

    assertThat(configurable.getEnableOfflineMode().isSelected).isFalse()
    assertThat(configurable.getForceOpen().isSelected).isTrue()
    assertThat(configurable.getDriverClass().text).isEqualTo("Driver")
    assertThat(configurable.getConnectionClass().text).isEqualTo("Connection")
    assertThat(configurable.getIgnoreFrameworkApi().isSelected).isTrue()
  }

  @Test
  fun apply() {
    settings.isOfflineModeEnabled = false
    settings.isForceOpen = true
    projectSettings.additionalDriverClass = "Driver"
    projectSettings.additionalConnectionClass = "Connection"
    projectSettings.isIgnoreFrameworkApi = false
    val configurable = createConfigurable()

    configurable.getEnableOfflineMode().isSelected = true
    configurable.getForceOpen().isSelected = false
    configurable.getDriverClass().text = "Driver1"
    configurable.getConnectionClass().text = "Connection1"
    configurable.getIgnoreFrameworkApi().isSelected = true
    configurable.apply()

    assertThat(settings.isOfflineModeEnabled).isTrue()
    assertThat(settings.isForceOpen).isFalse()
    assertThat(projectSettings.additionalDriverClass).isEqualTo("Driver1")
    assertThat(projectSettings.additionalConnectionClass).isEqualTo("Connection1")
    assertThat(projectSettings.isIgnoreFrameworkApi).isTrue()
  }

  @Test
  fun reset() {
    settings.isOfflineModeEnabled = false
    settings.isForceOpen = true
    projectSettings.additionalDriverClass = "Driver"
    projectSettings.additionalConnectionClass = "Connection"
    projectSettings.isIgnoreFrameworkApi = true
    val configurable = createConfigurable()

    configurable.getEnableOfflineMode().isSelected = true
    configurable.getForceOpen().isSelected = false
    configurable.getDriverClass().text = "Driver1"
    configurable.getConnectionClass().text = "Connection1"
    configurable.getIgnoreFrameworkApi().isSelected = false
    configurable.reset()

    assertThat(configurable.getEnableOfflineMode().isSelected).isFalse()
    assertThat(configurable.getForceOpen().isSelected).isTrue()
    assertThat(configurable.getDriverClass().text).isEqualTo("Driver")
    assertThat(configurable.getConnectionClass().text).isEqualTo("Connection")
    assertThat(configurable.getIgnoreFrameworkApi().isSelected).isTrue()
  }

  @Test
  fun isModified_offlineMode() {
    settings.isOfflineModeEnabled = true
    val configurable = createConfigurable()
    val offlineMode = configurable.getEnableOfflineMode()
    assertThat(configurable.isModified).isFalse()

    offlineMode.isSelected = false

    assertThat(configurable.isModified).isTrue()
  }

  @Test
  fun isModified_forceOpen() {
    settings.isForceOpen = false
    val configurable = createConfigurable()
    val forceOpen = configurable.getForceOpen()
    assertThat(configurable.isModified).isFalse()

    forceOpen.isSelected = true

    assertThat(configurable.isModified).isTrue()
  }

  @Test
  fun isModified_driverClass() {
    projectSettings.additionalDriverClass = "Foo"
    val configurable = createConfigurable()
    val driverClass = configurable.getDriverClass()
    assertThat(configurable.isModified).isFalse()

    driverClass.text = "Bar"

    assertThat(configurable.isModified).isTrue()
  }

  @Test
  fun isModified_connectionClass() {
    projectSettings.additionalConnectionClass = "Foo"
    val configurable = createConfigurable()
    val connectionClass = configurable.getConnectionClass()
    assertThat(configurable.isModified).isFalse()

    connectionClass.text = "Bar"

    assertThat(configurable.isModified).isTrue()
  }

  @Test
  fun isModified_ignoreFrameworkApi() {
    projectSettings.isIgnoreFrameworkApi = false
    val configurable = createConfigurable()
    val ignoreFrameworkApi = configurable.getIgnoreFrameworkApi()
    assertThat(configurable.isModified).isFalse()

    ignoreFrameworkApi.isSelected = true

    assertThat(configurable.isModified).isTrue()
  }

  @Test
  fun testForceOpenCheckbox_flagDisabled_notShown() {
    val provider = DatabaseInspectorConfigurableProvider(project)
    assertThat(provider.createConfigurable().hasNamedComponent<JCheckBox>("forceOpen")).isTrue()

    EXPERIMENTAL_FLAG.override(false)
    assertThat(provider.createConfigurable().hasNamedComponent<JCheckBox>("forceOpen")).isFalse()
  }

  @Test
  fun testAdditionalClasses_flagDisabled_notShown() {
    val provider = DatabaseInspectorConfigurableProvider(project)
    assertThat(provider.createConfigurable().hasNamedComponent<TextAccessor>("driverClass"))
      .isTrue()
    assertThat(provider.createConfigurable().hasNamedComponent<TextAccessor>("connectionClass"))
      .isTrue()

    ADDITIONAL_DRIVER_FLAG.override(false)
    assertThat(provider.createConfigurable().hasNamedComponent<TextAccessor>("driverClass"))
      .isFalse()
    assertThat(provider.createConfigurable().hasNamedComponent<TextAccessor>("connectionClass"))
      .isFalse()
  }

  @Test
  fun testIsIgnoreFramework_flagDisabled_notShown() {
    val provider = DatabaseInspectorConfigurableProvider(project)
    assertThat(provider.createConfigurable().hasNamedComponent<JCheckBox>("ignoreFrameworkApi"))
      .isTrue()

    ADDITIONAL_DRIVER_FLAG.override(false)
    assertThat(provider.createConfigurable().hasNamedComponent<JCheckBox>("ignoreFrameworkApi"))
      .isFalse()
  }

  private fun createConfigurable(): Configurable {
    val provider = DatabaseInspectorConfigurableProvider(project)
    return provider.createConfigurable()
  }
}

private fun Configurable.getEnableOfflineMode() = getNamedComponent<JCheckBox>("enableOfflineMode")

private fun Configurable.getForceOpen() = getNamedComponent<JCheckBox>("forceOpen")

private fun Configurable.getDriverClass() = getNamedComponent<TextAccessor>("driverClass")

private fun Configurable.getConnectionClass() = getNamedComponent<TextAccessor>("connectionClass")

private fun Configurable.getIgnoreFrameworkApi() =
  getNamedComponent<JCheckBox>("ignoreFrameworkApi")

private inline fun <reified T : Any> Configurable.getNamedComponent(name: String): T {
  val component = createComponent() ?: fail("Unexpected null component")
  return component.getDescendant<T> { (it as? Component)?.name == name }
}

private inline fun <reified T : Any> Configurable.hasNamedComponent(name: String): Boolean {
  val component = createComponent() ?: fail("Unexpected null component")
  return component.findDescendant<T> { (it as? Component)?.name == name } != null
}
