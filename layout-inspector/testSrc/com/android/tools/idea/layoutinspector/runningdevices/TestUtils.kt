/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.properties.DimensionUnitAction
import com.android.tools.idea.layoutinspector.properties.PROPERTIES_COMPONENT_NAME
import com.android.tools.idea.layoutinspector.runningdevices.actions.GearAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.HorizontalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.LeftVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.RightVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapHorizontalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapLeftVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapRightVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.actions.VerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.ui.LayoutInspectorRenderer
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.tree.RootPanel
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.SingleDeviceSelectProcessAction
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.Splitter
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel

/** Proxy used to avoid exposing the entire [LayoutInspectorSettings]. */
object EmbeddedLayoutInspectorSettingsProxy {
  var enableEmbeddedLayoutInspector: Boolean
    set(value) {
      LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled = value
    }
    get() = LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled

  var enableAutoConnect: Boolean
    set(value) {
      LayoutInspectorSettings.getInstance().autoConnectEnabled = value
    }
    get() = LayoutInspectorSettings.getInstance().autoConnectEnabled
}

/**
 * Utility function used to enable and disable embedded Layout Inspector in tests. It takes care of
 * restoring the settings state at the end of the test, and provides
 * [EmbeddedLayoutInspectorSettingsProxy], a utility to enable and disable embedded Layout Inspector
 * during the test.
 */
fun withEmbeddedLayoutInspector(
  enabled: Boolean = true,
  block: EmbeddedLayoutInspectorSettingsProxy.() -> Unit,
) {
  val settings = EmbeddedLayoutInspectorSettingsProxy
  val prev = settings.enableEmbeddedLayoutInspector
  settings.enableEmbeddedLayoutInspector = enabled
  try {
    block(settings)
  } finally {
    settings.enableEmbeddedLayoutInspector = prev
  }
}

/**
 * Utility function used to enable and disable auto connect in tests. It takes care of restoring the
 * settings state at the end of the test, and provides [EmbeddedLayoutInspectorSettingsProxy], a
 * utility to enable and auto connect during the test.
 */
fun withAutoConnect(
  enabled: Boolean = true,
  block: EmbeddedLayoutInspectorSettingsProxy.() -> Unit,
) {
  val settings = EmbeddedLayoutInspectorSettingsProxy
  val prev = settings.enableAutoConnect
  settings.enableAutoConnect = enabled
  try {
    block(settings)
  } finally {
    settings.enableAutoConnect = prev
  }
}

fun Component.allParents(): List<Container> {
  val parents = mutableListOf<Container>()
  var component = this
  while (component.parent != null) {
    parents.add(component.parent)
    component = component.parent
  }
  return parents
}

fun Container.allChildren(): List<Component> {
  val children = mutableListOf<Component>()
  for (component in components) {
    children.add(component)
    if (component is Container) {
      children.addAll(component.allChildren())
    }
  }
  return children
}

private fun WorkBench<LayoutInspector>.getTreePanel() =
  allChildren().filterIsInstance<RootPanel>().first()

private fun WorkBench<LayoutInspector>.getAttributesPanel() =
  allChildren().first { it.name == PROPERTIES_COMPONENT_NAME }

fun verifyUiInjected(
  uiConfig: UiConfig,
  content: Component,
  container: Container,
  displayView: AbstractDisplayView,
) {
  val workbench =
    when (uiConfig) {
      UiConfig.HORIZONTAL -> {
        val splitter = content.allParents().filterIsInstance<Splitter>().first()
        val workbench =
          splitter.allChildren().filterIsInstance<WorkBench<LayoutInspector>>().first()

        val left = workbench.getBottomComponents(Side.LEFT)?.first()!!
        val right = workbench.getBottomComponents(Side.RIGHT)?.first()!!

        assertThat(left.allChildren()).contains(workbench.getTreePanel())
        assertThat(right.allChildren()).contains(workbench.getAttributesPanel())

        workbench
      }
      UiConfig.HORIZONTAL_SWAP -> {
        val splitter = content.allParents().filterIsInstance<Splitter>().first()
        val workbench =
          splitter.allChildren().filterIsInstance<WorkBench<LayoutInspector>>().first()

        val left = workbench.getBottomComponents(Side.LEFT)?.first()!!
        val right = workbench.getBottomComponents(Side.RIGHT)?.first()!!

        assertThat(left.allChildren()).contains(workbench.getAttributesPanel())
        assertThat(right.allChildren()).contains(workbench.getTreePanel())

        workbench
      }
      UiConfig.VERTICAL -> {
        assertThat(content.allParents().filterIsInstance<Splitter>()).isEmpty()
        val workbench = content.allParents().filterIsInstance<WorkBench<LayoutInspector>>().first()

        val left = workbench.getBottomComponents(Side.LEFT)?.first()!!
        val right = workbench.getBottomComponents(Side.RIGHT)?.first()!!

        assertThat(left.allChildren()).contains(workbench.getTreePanel())
        assertThat(right.allChildren()).contains(workbench.getAttributesPanel())

        workbench
      }
      UiConfig.VERTICAL_SWAP -> {
        assertThat(content.allParents().filterIsInstance<Splitter>()).isEmpty()
        val workbench = content.allParents().filterIsInstance<WorkBench<LayoutInspector>>().first()

        val left = workbench.getBottomComponents(Side.LEFT)?.first()!!
        val right = workbench.getBottomComponents(Side.RIGHT)?.first()!!

        assertThat(left.allChildren()).contains(workbench.getAttributesPanel())
        assertThat(right.allChildren()).contains(workbench.getTreePanel())

        workbench
      }
      UiConfig.LEFT_VERTICAL -> {
        assertThat(content.allParents().filterIsInstance<Splitter>()).isEmpty()
        val workbench = content.allParents().filterIsInstance<WorkBench<LayoutInspector>>().first()

        val topLeft = workbench.getTopComponents(Side.LEFT)?.first()!!
        val bottomLeft = workbench.getBottomComponents(Side.LEFT)?.first()!!

        assertThat(topLeft.allChildren()).contains(workbench.getTreePanel())
        assertThat(bottomLeft.allChildren()).contains(workbench.getAttributesPanel())

        workbench
      }
      UiConfig.LEFT_VERTICAL_SWAP -> {
        assertThat(content.allParents().filterIsInstance<Splitter>()).isEmpty()
        val workbench = content.allParents().filterIsInstance<WorkBench<LayoutInspector>>().first()

        val topLeft = workbench.getTopComponents(Side.LEFT)?.first()!!
        val bottomLeft = workbench.getBottomComponents(Side.LEFT)?.first()!!

        assertThat(topLeft.allChildren()).contains(workbench.getAttributesPanel())
        assertThat(bottomLeft.allChildren()).contains(workbench.getTreePanel())

        workbench
      }
      UiConfig.RIGHT_VERTICAL -> {
        assertThat(content.allParents().filterIsInstance<Splitter>()).isEmpty()
        val workbench = content.allParents().filterIsInstance<WorkBench<LayoutInspector>>().first()

        val topRight = workbench.getTopComponents(Side.RIGHT)?.first()!!
        val bottomRight = workbench.getBottomComponents(Side.RIGHT)?.first()!!

        assertThat(topRight.allChildren()).contains(workbench.getTreePanel())
        assertThat(bottomRight.allChildren()).contains(workbench.getAttributesPanel())

        workbench
      }
      UiConfig.RIGHT_VERTICAL_SWAP -> {
        assertThat(content.allParents().filterIsInstance<Splitter>()).isEmpty()
        val workbench = content.allParents().filterIsInstance<WorkBench<LayoutInspector>>().first()

        val topRight = workbench.getTopComponents(Side.RIGHT)?.first()!!
        val bottomRight = workbench.getBottomComponents(Side.RIGHT)?.first()!!

        assertThat(topRight.allChildren()).contains(workbench.getAttributesPanel())
        assertThat(bottomRight.allChildren()).contains(workbench.getTreePanel())

        workbench
      }
    }

  assertThat(workbench.isFocusCycleRoot).isFalse()

  verifyToolbar(container)
  verifyWorkbench(workbench)

  val inspectorBanner = container.allChildren().filterIsInstance<InspectorBanner>().first()
  assertThat(inspectorBanner).isNotNull()

  assertThat(displayView.allChildren().filterIsInstance<LayoutInspectorRenderer>()).hasSize(1)
}

fun verifyUiRemoved(content: Component, container: Container, displayView: AbstractDisplayView) {
  assertThat(content.allParents().filterIsInstance<Splitter>()).hasSize(0)
  assertThat(content.allParents().filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(0)
  assertThat(container.allChildren().filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(0)
  assertThat(container.allChildren().filterIsInstance<Splitter>()).hasSize(0)
  assertThat(content.parent).isEqualTo(container)

  val toolbars =
    container.allChildren().filterIsInstance<ActionToolbar>().filter {
      it.component.name == "LayoutInspector.MainToolbar"
    }
  assertThat(toolbars).hasSize(0)

  val inspectorBanner = container.allChildren().filterIsInstance<InspectorBanner>()
  assertThat(inspectorBanner).hasSize(0)

  assertThat(displayView.allChildren().filterIsInstance<LayoutInspectorRenderer>()).hasSize(0)
}

private fun verifyToolbar(container: Container) {
  val toolbars =
    container.allChildren().filterIsInstance<ActionToolbar>().filter {
      it.component.name == "LayoutInspector.MainToolbar"
    }

  assertThat(toolbars).hasSize(1)
  val toolbar = toolbars.first()

  assertThat(toolbar.actions.filterIsInstance<SingleDeviceSelectProcessAction>()).hasSize(1)
  assertThat(toolbar.actions.filterIsInstance<ToggleDeepInspectAction>()).hasSize(1)
  assertThat(toolbar.actions.filterIsInstance<GearAction>()).hasSize(1)

  val gearAction = toolbar.actions.filterIsInstance<GearAction>().first()
  assertThat(gearAction.actions.toList()).hasSize(10)
  assertThat(gearAction.actions.filterIsInstance<VerticalSplitAction>()).hasSize(1)
  assertThat(gearAction.actions.filterIsInstance<SwapVerticalSplitAction>()).hasSize(1)
  assertThat(gearAction.actions.filterIsInstance<HorizontalSplitAction>()).hasSize(1)
  assertThat(gearAction.actions.filterIsInstance<SwapHorizontalSplitAction>()).hasSize(1)
  assertThat(gearAction.actions.filterIsInstance<LeftVerticalSplitAction>()).hasSize(1)
  assertThat(gearAction.actions.filterIsInstance<SwapLeftVerticalSplitAction>()).hasSize(1)
  assertThat(gearAction.actions.filterIsInstance<RightVerticalSplitAction>()).hasSize(1)
  assertThat(gearAction.actions.filterIsInstance<SwapRightVerticalSplitAction>()).hasSize(1)
  assertThat(gearAction.actions.filterIsInstance<DimensionUnitAction>()).hasSize(1)

  assertThat(container.allChildren().filter { it.name == "LayoutInspectorToolbarTitleLabel" })
    .hasSize(1)
}

private fun verifyWorkbench(workbench: WorkBench<LayoutInspector>) {
  // Assert the other gear actions from the side panels are not visible
  assertThat(
      workbench
        .allChildren()
        .filterIsInstance<JPanel>()
        .flatMap { it.allChildren() }
        .filterIsInstance<ActionToolbar>()
        .flatMap { it.component.allChildren() }
        .filterIsInstance<ActionButton>()
        .filter { it.presentation.text.contains("More Options") }
    )
    .isEmpty()

  // Assert the hide actions from the side panels are not visible
  assertThat(
      workbench
        .allChildren()
        .filterIsInstance<JPanel>()
        .flatMap { it.allChildren() }
        .filterIsInstance<ActionToolbar>()
        .flatMap { it.component.allChildren() }
        .filterIsInstance<ActionButton>()
        .filter { it.presentation.text.contains("Hide") }
    )
    .isEmpty()
}
