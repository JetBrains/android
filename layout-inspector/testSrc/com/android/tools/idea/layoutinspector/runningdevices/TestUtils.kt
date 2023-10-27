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

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.runningdevices.actions.GearAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.ui.LayoutInspectorRenderer
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
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
  block: EmbeddedLayoutInspectorSettingsProxy.() -> Unit
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
  block: EmbeddedLayoutInspectorSettingsProxy.() -> Unit
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

fun verifyUiInjected(
  uiConfig: UiConfig,
  content: Component,
  container: Container,
  displayView: AbstractDisplayView
) {
  val workbench =
    when (uiConfig) {
      UiConfig.HORIZONTAL -> {
        val splitter = content.allParents().filterIsInstance<Splitter>().first()
        splitter.allChildren().filterIsInstance<WorkBench<LayoutInspector>>().first()
      }
      UiConfig.VERTICAL -> {
        assertThat(content.allParents().filterIsInstance<Splitter>()).isEmpty()
        content.allParents().filterIsInstance<WorkBench<LayoutInspector>>().first()
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
