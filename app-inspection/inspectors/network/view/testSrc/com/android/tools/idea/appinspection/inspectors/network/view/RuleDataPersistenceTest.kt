/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.inspectors.network.model.FakeCodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.TestNetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleDataListener
import com.android.tools.idea.appinspection.inspectors.network.view.rules.RulesTableView
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Rule
import org.junit.Test

class RuleDataPersistenceTest {

  @get:Rule
  val projectRule = ProjectRule()
  private lateinit var scope: CoroutineScope

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun testRulesPersistence() {
    // Setup
    val firstRule = RuleData(100, "First Rule", true)
    val secondRule = RuleData(200, "Second Rule", false)
    val thirdRule = RuleData(300, "Third Rule", false)

    var rulesTableView = createNewRulesTableView()
    rulesTableView.tableModel.addRow(firstRule)
    rulesTableView.tableModel.addRow(secondRule)
    var items = rulesTableView.tableModel.items

    // Assert
    assertThat(items.size).isEqualTo(2)
    assertThat(rulesTableView.persistentStateComponent.myRuleDataState.rulesList.size).isEqualTo(2)
    assertThat(items.first().id).isEqualTo(100)
    assertThat(items.last().id).isEqualTo(200)

    // A new RulesTableView is created when the app restarts. Simulate the same here.
    rulesTableView = createNewRulesTableView()
    items = rulesTableView.tableModel.items

    // Assert
    assertThat(items.size).isEqualTo(2)
    assertThat(rulesTableView.persistentStateComponent.myRuleDataState.rulesList.size).isEqualTo(2)

    assertRuleValues(items[0], firstRule)
    assertRuleValues(items[1], secondRule)

    // Add Rule
    rulesTableView.tableModel.addRow(thirdRule)

    // Assert
    assertThat(rulesTableView.tableModel.items.size).isEqualTo(3)
    assertThat(rulesTableView.persistentStateComponent.myRuleDataState.rulesList.size).isEqualTo(3)

    // A new RulesTableView is created when the app restarts. Simulate the same here.
    rulesTableView = createNewRulesTableView()
    items = rulesTableView.tableModel.items

    // Assert
    assertThat(rulesTableView.tableModel.items.size).isEqualTo(3)
    assertThat(rulesTableView.persistentStateComponent.myRuleDataState.rulesList.size).isEqualTo(3)

    assertRuleValues(items[0], firstRule)
    assertRuleValues(items[1], secondRule)
    assertRuleValues(items[2], thirdRule)

    // Remove rule
    rulesTableView.tableModel.removeRow(0)

    // Assert
    assertThat(rulesTableView.tableModel.items.size).isEqualTo(2)
    assertThat(rulesTableView.persistentStateComponent.myRuleDataState.rulesList.size).isEqualTo(2)
    assertRuleValues(items[0], secondRule)

    // A new RulesTableView is created when the app restarts. Simulate the same here.
    rulesTableView = createNewRulesTableView()
    items = rulesTableView.tableModel.items

    // Assert
    assertThat(rulesTableView.tableModel.items.size).isEqualTo(2)
    assertThat(rulesTableView.persistentStateComponent.myRuleDataState.rulesList.size).isEqualTo(2)
    assertRuleValues(items[0], secondRule)
    assertRuleValues(items[1], thirdRule)

    // Change name of a rule
    items[0].name = "Changed Name"

    // A new RulesTableView is created when the app restarts. Simulate the same here.
    rulesTableView = createNewRulesTableView()
    items = rulesTableView.tableModel.items

    // Assert name has changed
    assertThat(items[0].name).isEqualTo("Changed Name")
  }

  @Test
  fun testPersistenceInOfflineMode() {
    // Setup
    var failTest = false
    var rulesTableView = createNewRulesTableView()
    // Rule with custom listener that will fail the test if the coroutine is executed
    val rule = RuleData(1, "First Rule", true, object: RuleDataListener {
      override fun onRuleNameChanged(ruleData: RuleData) { /* scope is not used in this listener */ }
      override fun onRuleIsActiveChanged(ruleData: RuleData) { scope.launch { failTest = true } }
      override fun onRuleDataChanged(ruleData: RuleData) { scope.launch { failTest = true } }
    })

    rulesTableView.tableModel.addRow(rule)
    var items = rulesTableView.tableModel.items

    assertThat(items.size).isEqualTo(1)
    assertRuleValues(items[0], rule)

    // Cancel scope to simulate offline mode (app is stopped)
    scope.cancel()

    // Update rule to invoke all three listeners
    items[0].name = "Changed Name"
    items[0].criteria.port = "123"
    items[0].isActive = false

    // Assert that the test did not fail due to scope.launch being executed
    assertThat(failTest).isFalse()

    // Simulate an app restart
    rulesTableView = createNewRulesTableView()
    items = rulesTableView.tableModel.items

    // Assert that offline changes were persisted
    assertThat(items.size).isEqualTo(1)
    assertThat(items[0].id).isEqualTo(1)
    assertThat(items[0].name).isEqualTo("Changed Name")
    assertThat(items[0].criteria.port).isEqualTo("123")
    assertThat(items[0].isActive).isEqualTo(false)
  }

  private fun createNewRulesTableView() : RulesTableView {
    val services = TestNetworkInspectorServices(FakeCodeNavigationProvider(), FakeTimer())
    if(::scope.isInitialized) scope.cancel()
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    val model = NetworkInspectorModel(services, FakeNetworkInspectorDataSource(), scope)
    return RulesTableView(projectRule.project, services.client, scope, model, services.usageTracker)
  }

  private fun assertRuleValues(actualRuleData: RuleData, expectedRuleData: RuleData) {
    assertThat(actualRuleData.id).isEqualTo(expectedRuleData.id)
    assertThat(actualRuleData.name).isEqualTo(expectedRuleData.name)
    assertThat(actualRuleData.isActive).isEqualTo(expectedRuleData.isActive)
  }
}