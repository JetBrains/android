/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.editor

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JTable

class InstantAppRunConfigurationsDialogTest {
  lateinit var myRunConfiguration: AndroidRunConfiguration
  var parameters = DynamicFeaturesParameters()

  @get:Rule
  val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.INSTANT_APP_WITH_DYNAMIC_FEATURES)

  @Before
  fun setUp() {
    StudioFlags.UAB_ENABLE_NEW_INSTANT_APP_RUN_CONFIGURATIONS.override(true)

    val configurationFactory = AndroidRunConfigurationType.getInstance().factory
    myRunConfiguration = AndroidRunConfiguration(projectRule.project, configurationFactory)
    parameters.setActiveModule(projectRule.module, DynamicFeaturesParameters.AvailableDeployTypes.INSTANT_AND_INSTALLED)
  }

  private fun getFeatureNameCellRenderer(table: JTable, row: Int, column: Int): DynamicFeaturesParameters.FeatureNameCellRenderer {
    return table.getCellRenderer(row, column).getTableCellRendererComponent(
      table,
      table.model.getValueAt(row, column),
      table.selectionModel.isSelectedIndex(row),
      true,
      row,
      column
    ) as DynamicFeaturesParameters.FeatureNameCellRenderer
  }

  @Test
  fun testVerifyDynamicFeatureAnnotations() {
    Truth.assertThat(getFeatureNameCellRenderer(parameters.tableComponent, 1, 1).text).contains(" (not instant app enabled)")
    Truth.assertThat(getFeatureNameCellRenderer(parameters.tableComponent, 2, 1).text).doesNotContain(" (not instant app enabled)")
  }

  @Test
  fun testVerifyBaseModuleAnnotations() {
    Truth.assertThat(getFeatureNameCellRenderer(parameters.tableComponent, 0, 1).text).contains(" (base)")
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(0, 0)).isFalse()
  }

  @Test
  fun testVerifyInstantStateUpdate() {
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(0, 0)).isFalse()
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(1, 0)).isTrue()
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(2, 0)).isTrue()
    Truth.assertThat(parameters.tableComponent.model.getValueAt(0, 0)).isEqualTo(true)
    Truth.assertThat(parameters.tableComponent.model.getValueAt(1, 0)).isEqualTo(true)
    Truth.assertThat(parameters.tableComponent.model.getValueAt(2, 0)).isEqualTo(true)

    parameters.updateBasedOnInstantState(projectRule.module, true)
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(0, 0)).isFalse()
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(1, 0)).isFalse()
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(2, 0)).isTrue()
    Truth.assertThat(parameters.tableComponent.model.getValueAt(0, 0)).isEqualTo(true)
    Truth.assertThat(parameters.tableComponent.model.getValueAt(1, 0)).isEqualTo(false)
    Truth.assertThat(parameters.tableComponent.model.getValueAt(2, 0)).isEqualTo(true)

    parameters.updateBasedOnInstantState(projectRule.module, false)
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(0, 0)).isFalse()
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(1, 0)).isTrue()
    Truth.assertThat(parameters.tableComponent.model.isCellEditable(2, 0)).isTrue()
    Truth.assertThat(parameters.tableComponent.model.getValueAt(0, 0)).isEqualTo(true)
    Truth.assertThat(parameters.tableComponent.model.getValueAt(1, 0)).isEqualTo(false)
    Truth.assertThat(parameters.tableComponent.model.getValueAt(2, 0)).isEqualTo(true)
  }
}
