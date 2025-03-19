/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.settingssync.onboarding

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.settingssync.onboarding.Category.Companion.DESCRIPTORS
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.core.SettingsSyncBundle
import com.intellij.settingsSync.core.SettingsSyncState
import com.intellij.settingsSync.core.SettingsSyncStateHolder
import com.intellij.settingsSync.core.config.EDITOR_FONT_SUBCATEGORY_ID
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class HierarchicalCheckboxesKtTest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun `test ui display`() {
    val states =
      mutableStateListOf<CheckboxNode>().apply {
        addAll(populateCheckboxNodes(SettingsSyncStateHolder()))
      }

    composeTestRule.setContent { HierarchicalCheckboxes(states) }

    DESCRIPTORS.forEach {
      // check category name
      composeTestRule.onNodeWithText(it.name).assertIsDisplayed()

      // check category description
      if (it.description.isNotEmpty()) {
        composeTestRule.onNodeWithText(it.description).assertIsDisplayed()
      }
    }

    // check "configure" dropdown links
    composeTestRule.onAllNodesWithText("Configure").assertCountEquals(2)
  }

  @Test
  fun `configure link for ui group is hidden`() {
    val initialSyncState =
      SettingsSyncStateHolder().apply {
        setCategoryEnabled(SettingsCategory.UI, false)
        setSubcategoryEnabled(SettingsCategory.UI, EDITOR_FONT_SUBCATEGORY_ID, false)
      }

    val nodes = populateCheckboxNodes(initialSyncState)
    composeTestRule.setContent { HierarchicalCheckboxes(nodes) }

    // verify initial state: parent and children checkboxes are "off".
    val uiNode = nodes.findNodeByCategory(SettingsCategory.UI)
    assertThat(uiNode.isCheckedState).isEqualTo(ToggleableState.Off)
    assertThat(uiNode.children.single().isCheckedState).isEqualTo(ToggleableState.Off)

    // verify "configure" link for ui group is hidden because the parent category is off and the
    // children list is not a complete list.
    composeTestRule.onAllNodesWithText("Configure").assertCountEquals(1)
  }

  @Test
  fun `test interaction, toggle all`() {
    val nodes = populateCheckboxNodes(SettingsSyncStateHolder())
    composeTestRule.setContent { HierarchicalCheckboxes(nodes) }

    // step1: unselect all via clicking on category name
    assertThat(nodes.all { it.isCheckedState == ToggleableState.On }).isTrue()
    DESCRIPTORS.forEach {
      composeTestRule.onNodeWithText(it.name, useUnmergedTree = true).performClick()
    }
    assertThat(nodes.none { it.isCheckedState == ToggleableState.On }).isTrue()

    // step2: select all via clicking on category description
    DESCRIPTORS.forEach {
      composeTestRule.onNodeWithText(it.name, useUnmergedTree = true).performClick()
    }
    assertThat(nodes.all { it.isCheckedState == ToggleableState.On }).isTrue()
  }

  @Test
  fun `interaction, configure plugin, off and on`() {
    val initialSyncState =
      SettingsSyncStateHolder().apply {
        setCategoryEnabled(SettingsCategory.PLUGINS, false)
        setSubcategoryEnabled(SettingsCategory.PLUGINS, BUNDLED_PLUGINS_ID, false)
        setSubcategoryEnabled(SettingsCategory.PLUGINS, "plugin_a", false)
        setSubcategoryEnabled(SettingsCategory.PLUGINS, "plugin_b", false)
        setSubcategoryEnabled(SettingsCategory.PLUGINS, "plugin_c", false)
      }

    val nodes = populateCheckboxNodes(initialSyncState)
    composeTestRule.setContent { HierarchicalCheckboxes(nodes) }

    val pluginsNode = nodes.findNodeByCategory(SettingsCategory.PLUGINS)
    // verify initial state: parent and children checkboxes are off
    assertThat(pluginsNode.isCheckedState).isEqualTo(ToggleableState.Off)
    assertThat(pluginsNode.children.single { it.id == "plugin_a" }.isCheckedState)
      .isEqualTo(ToggleableState.Off)
    assertThat(pluginsNode.children.single { it.id == "plugin_b" }.isCheckedState)
      .isEqualTo(ToggleableState.Off)
    assertThat(pluginsNode.children.single { it.id == "plugin_c" }.isCheckedState)
      .isEqualTo(ToggleableState.Off)

    // click "configure" for plugins
    composeTestRule.onAllNodesWithText("Configure")[1].performClick()
    // select an entry
    composeTestRule.onNodeWithText("Bundled plugins").assertIsDisplayed().performClick()

    // verify that the parent checkbox is "indeterminate".
    assertThat(pluginsNode.isCheckedState).isEqualTo(ToggleableState.Indeterminate)
    assertThat(pluginsNode.children.single { it.id == BUNDLED_PLUGINS_ID }.isCheckedState)
      .isEqualTo(ToggleableState.On)

    // select the rest of entries
    composeTestRule.onNodeWithText("Plugin A").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Plugin B").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Plugin C").assertIsDisplayed().performClick()
    assertThat(pluginsNode.isCheckedState).isEqualTo(ToggleableState.On)
    assertThat(pluginsNode.children.single { it.id == "plugin_a" }.isCheckedState)
      .isEqualTo(ToggleableState.On)
    assertThat(pluginsNode.children.single { it.id == "plugin_b" }.isCheckedState)
      .isEqualTo(ToggleableState.On)
    assertThat(pluginsNode.children.single { it.id == "plugin_c" }.isCheckedState)
      .isEqualTo(ToggleableState.On)
  }

  @Test
  fun `interaction, configure plugin, on and off`() {
    val nodes = populateCheckboxNodes(SettingsSyncStateHolder())
    composeTestRule.setContent { HierarchicalCheckboxes(nodes) }

    val pluginsNode = nodes.findNodeByCategory(SettingsCategory.PLUGINS)
    // verify initial state: parent and children checkboxes are on
    assertThat(pluginsNode.isCheckedState).isEqualTo(ToggleableState.On)
    assertThat(pluginsNode.children.single { it.id == "plugin_a" }.isCheckedState)
      .isEqualTo(ToggleableState.On)
    assertThat(pluginsNode.children.single { it.id == "plugin_b" }.isCheckedState)
      .isEqualTo(ToggleableState.On)
    assertThat(pluginsNode.children.single { it.id == "plugin_c" }.isCheckedState)
      .isEqualTo(ToggleableState.On)

    // click "configure" for plugins
    composeTestRule.onAllNodesWithText("Configure")[1].performClick()
    // select an entry
    composeTestRule.onNodeWithText("Bundled plugins").assertIsDisplayed().performClick()

    // verify that the parent checkbox is "indeterminate".
    assertThat(pluginsNode.isCheckedState).isEqualTo(ToggleableState.Indeterminate)
    assertThat(pluginsNode.children.single { it.id == BUNDLED_PLUGINS_ID }.isCheckedState)
      .isEqualTo(ToggleableState.Off)

    // select the rest of entries
    composeTestRule.onNodeWithText("Plugin A").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Plugin B").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Plugin C").assertIsDisplayed().performClick()
    assertThat(pluginsNode.isCheckedState).isEqualTo(ToggleableState.Off)
    assertThat(pluginsNode.children.single { it.id == "plugin_a" }.isCheckedState)
      .isEqualTo(ToggleableState.Off)
    assertThat(pluginsNode.children.single { it.id == "plugin_b" }.isCheckedState)
      .isEqualTo(ToggleableState.Off)
    assertThat(pluginsNode.children.single { it.id == "plugin_c" }.isCheckedState)
      .isEqualTo(ToggleableState.Off)
  }

  @Test
  fun `interaction, configure ui, on and indeterminate and on`() {
    val nodes = populateCheckboxNodes(SettingsSyncStateHolder())
    composeTestRule.setContent { HierarchicalCheckboxes(nodes) }

    // verify initial state: parent and children checkboxes are "on".
    val uiNode = nodes.findNodeByCategory(SettingsCategory.UI)
    assertThat(uiNode.isCheckedState).isEqualTo(ToggleableState.On)
    assertThat(uiNode.children.single().isCheckedState).isEqualTo(ToggleableState.On)

    // click "configure" for ui
    composeTestRule.onAllNodesWithText("Configure")[0].performClick()
    // unselect the only checkbox
    composeTestRule.onNodeWithText("Editor font").assertIsDisplayed().performClick()

    // verify child checkbox is "off", but parent checkbox is "indeterminate" because our children
    // list is not a complete list.
    assertThat(uiNode.isCheckedState).isEqualTo(ToggleableState.Indeterminate)
    assertThat(uiNode.children.single().isCheckedState).isEqualTo(ToggleableState.Off)

    // select the only checkbox
    composeTestRule.onNodeWithText("Editor font").assertIsDisplayed().performClick()

    // verify both the parent and child checkbox are "on" again.
    assertThat(uiNode.isCheckedState).isEqualTo(ToggleableState.On)
    assertThat(uiNode.children.single().isCheckedState).isEqualTo(ToggleableState.On)
  }
}

private fun findDescriptor(category: SettingsCategory): Category {
  return DESCRIPTORS.single { it.category == category }
}

private fun List<CheckboxNode>.findNodeByCategory(category: SettingsCategory): CheckboxNode {
  return single { it.label == findDescriptor(category).name }
}

internal fun populateCheckboxNodes(state: SettingsSyncState): List<CheckboxNode> {
  return DESCRIPTORS.toMutableList()
    .apply {
      replaceAll { item ->
        // No plugin manager installed due to current test environment setup.
        // So replace it with our fake plugins to avoid NullPointerException.
        if (item.category == SettingsCategory.PLUGINS) {
          Category(
            SettingsCategory.PLUGINS,
            secondaryGroup =
              object : SyncSubcategoryGroup {
                override fun getDescriptors(): List<SettingsSyncSubcategoryDescriptor> {
                  return listOf(
                    // bundled plugins entry
                    SettingsSyncSubcategoryDescriptor(
                      name = SettingsSyncBundle.message("plugins.bundled"),
                      id = BUNDLED_PLUGINS_ID,
                      isSelected = true,
                      isSubGroupEnd = true,
                    ),
                    // test pluginA
                    SettingsSyncSubcategoryDescriptor(
                      name = "Plugin A",
                      id = "plugin_a",
                      isSelected = true,
                      isSubGroupEnd = true,
                    ),
                    // test pluginA
                    SettingsSyncSubcategoryDescriptor(
                      name = "Plugin B",
                      id = "plugin_b",
                      isSelected = true,
                      isSubGroupEnd = true,
                    ),
                    // test pluginC
                    SettingsSyncSubcategoryDescriptor(
                      name = "Plugin C",
                      id = "plugin_c",
                      isSelected = true,
                      isSubGroupEnd = true,
                    ),
                  )
                }
              },
          )
        } else item
      }
    }
    .map { it.toCheckboxNode(state) }
    .toList()
}
