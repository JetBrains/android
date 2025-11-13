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
package com.android.tools.idea.layoutinspector.stateinspection

import com.android.flags.junit.FlagRule
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.COMPOSE3
import com.android.tools.idea.layoutinspector.model.COMPOSE4
import com.android.tools.idea.layoutinspector.model.COMPOSE5
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.All
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.None
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.Some
import com.android.tools.idea.layoutinspector.ui.LAYOUT_INSPECTOR_DATA_KEY
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunsInEdt
class StateReadMenuTest {
  private val disposableRule = DisposableRule()
  private val flagRule = FlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_STATE_READS, true)

  @get:Rule val rule = RuleChain(ApplicationRule(), disposableRule, flagRule, EdtRule())
  private lateinit var model: InspectorModel
  private lateinit var mockLayoutInspector: LayoutInspector
  private lateinit var client: InspectorClient
  private lateinit var event: AnActionEvent

  @Before
  fun setUp() {
    model =
      model(disposableRule.disposable) {
        view(
          ROOT,
          viewId = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "rootId"),
        ) {
          view(VIEW1) {
            compose(COMPOSE1, "Row") {
              compose(COMPOSE2, "Item") { compose(COMPOSE3, "Text") }
              compose(COMPOSE4, "Item") { compose(COMPOSE5, "Text") }
            }
          }
        }
      }

    val stats = SessionStatisticsImpl(ClientType.APP_INSPECTION_CLIENT)
    client = mock()
    whenever(client.capabilities)
      .thenReturn(
        setOf(Capability.CAN_OBSERVE_RECOMPOSE_STATE_READS, Capability.HAS_LINE_NUMBER_INFORMATION)
      )
    whenever(client.stats).thenReturn(stats)
    mockLayoutInspector = mock()
    whenever(mockLayoutInspector.currentClient).thenReturn(client)
    val context =
      SimpleDataContext.builder().add(LAYOUT_INSPECTOR_DATA_KEY, mockLayoutInspector).build()
    event = createFakeEvent(context)
  }

  @Test
  fun testNoLineInfo() {
    val compose1 = model[COMPOSE1] as ComposeViewNode
    whenever(client.capabilities).thenReturn(setOf(Capability.CAN_OBSERVE_RECOMPOSE_STATE_READS))
    val stateReadMenu = createStateReadMenuGroup(compose1, model)
    stateReadMenu.checkText(event, "Observe Recomposition (No Source Information Found)")
    stateReadMenu.checkIsNotEnabled(event)
  }

  @Test
  fun testStateReadsNotSupported() {
    whenever(client.capabilities).thenReturn(setOf(Capability.HAS_LINE_NUMBER_INFORMATION))
    val compose1 = model[COMPOSE1] as ComposeViewNode
    val stateReadMenu = createStateReadMenuGroup(compose1, model)
    stateReadMenu.checkText(event, "Observe Recomposition (Needs Compose 1.10.0+)")
    stateReadMenu.checkIsNotEnabled(event)
  }

  @Test
  fun testStateReadMenu() {
    val compose2 = model[COMPOSE2] as ComposeViewNode
    val compose3 = model[COMPOSE3] as ComposeViewNode
    val stateReadMenu = createStateReadMenuGroup(compose2, model)
    stateReadMenu.checkIsEnabled(event)
    val actions = stateReadMenu.children(event)
    assertThat(actions.map { it.templateText })
      .containsExactly("Observe Node", "Observe All", "Observe None")
    val observeNode = actions[0]
    observeNode.checkText(event, "Observe Node")
    ActionUtil.performAction(observeNode, event)
    assertThat(model.stateReadsModel.observedForStateReads.value).isEqualTo(Some(setOf(compose2)))
    observeNode.checkText(event, "Stop Observing Node")
    ActionUtil.performAction(observeNode, event)
    assertThat(model.stateReadsModel.observedForStateReads.value).isEqualTo(None)

    val observeAll = actions[1]
    observeAll.checkText(event, "Observe All")
    ActionUtil.performAction(observeAll, event)
    assertThat(model.stateReadsModel.observedForStateReads.value).isEqualTo(All)

    val observeNone = actions[2]
    observeNone.checkText(event, "Observe None")
    ActionUtil.performAction(observeNone, event)
    assertThat(model.stateReadsModel.observedForStateReads.value).isEqualTo(None)

    val data = DynamicLayoutInspectorSession.newBuilder()
    client.stats.save(data)
    assertThat(data.stateReads.observingAllSelected).isEqualTo(1)
    assertThat(data.stateReads.observingNodeByIdSelected).isEqualTo(2)
  }
}

private fun createFakeEvent(context: DataContext): AnActionEvent =
  createEvent(context, null, "", ActionUiKind.NONE, null)

private fun AnAction.checkText(event: AnActionEvent, expected: String) {
  event.presentation.copyFrom(templatePresentation)
  ActionUtil.updateAction(this, event)
  assertThat(event.presentation.text).isEqualTo(expected)
}

private fun AnAction.checkIsEnabled(event: AnActionEvent) = checkEnable(event, true)

private fun AnAction.checkIsNotEnabled(event: AnActionEvent) = checkEnable(event, false)

private fun AnAction.checkEnable(event: AnActionEvent, expected: Boolean) {
  event.presentation.copyFrom(templatePresentation)
  ActionUtil.updateAction(this, event)
  assertThat(event.presentation.isEnabled).isEqualTo(expected)
}

private fun AnAction.children(event: AnActionEvent): Array<AnAction> {
  val group = this as? ActionGroup ?: return emptyArray()
  @Suppress("OverrideOnly") return group.getChildren(event)
}
