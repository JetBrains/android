/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.recompositions

import com.android.tools.idea.layoutinspector.hasCapability
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorRecompositionModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.ui.LayoutInspectorRootPanel
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

const val STATE_READS_MIN_VERSION = "1.10.0"

/** Create an "Observe Recomposition" menu group. */
fun createObserveRecompositionMenuGroup(selected: ComposeViewNode, inspectorModel: InspectorModel): AnAction {
  return object : ActionGroup("Observe Recomposition", true) {
    override fun update(event: AnActionEvent) {
      val inspector = LayoutInspectorRootPanel.get(event)
      val hasLineNumberInfo = inspector.hasCapability(Capability.HAS_LINE_NUMBER_INFORMATION)
      val canObserveStateReads = inspector.hasCapability(Capability.CAN_OBSERVE_RECOMPOSE_STATE_READS)
      event.presentation.isEnabled = hasLineNumberInfo && canObserveStateReads
      event.presentation.text =
        when {
          !hasLineNumberInfo -> "$templateText (No Source Information Found)"
          !canObserveStateReads -> "$templateText (Needs Compose $STATE_READS_MIN_VERSION+)"
          else -> templateText
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
      val model = inspectorModel.recompositionModel
      val result = mutableListOf<AnAction>()
      result.add(ObserveNodeAction(model, selected))
      result.add(ObserveAllAction(model))
      result.add(ObserveNoneAction(model))
      return result.toTypedArray()
    }
  }
}

private class ObserveNodeAction(private val model: InspectorRecompositionModel, val topNode: ComposeViewNode) : AnAction("Observe Node") {
  override fun actionPerformed(event: AnActionEvent) {
    if (model.isNodeObserved(topNode)) {
      model.stopObservingNode(topNode)
    } else {
      model.observeNode(topNode)
    }
    LayoutInspectorRootPanel.get(event)?.currentClient?.stats?.observingSingleNodeSelected()

    // Close the StateInspectionPanel if nothing is observed:
    if (!model.isObservingAny()) {
      model.stopShowingRecompositionDetails()
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = !model.isObservingAll()
    event.presentation.text = if (model.isNodeObserved(topNode)) "Stop Observing Node" else "Observe Node"
  }
}

private class ObserveAllAction(private val model: InspectorRecompositionModel) : AnAction("Observe All") {
  override fun actionPerformed(event: AnActionEvent) {
    model.observeAll()
    LayoutInspectorRootPanel.get(event)?.currentClient?.stats?.observingAllSelected()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = !model.isObservingAll()
  }
}

private class ObserveNoneAction(private val model: InspectorRecompositionModel) : AnAction("Observe None") {
  override fun actionPerformed(event: AnActionEvent) {
    model.observeNone()
    LayoutInspectorRootPanel.get(event)?.currentClient?.stats?.observingNoneSelected()

    // Close the StateInspectionPanel since nothing is observed:
    model.stopShowingRecompositionDetails()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = model.isObservingAny()
  }
}
