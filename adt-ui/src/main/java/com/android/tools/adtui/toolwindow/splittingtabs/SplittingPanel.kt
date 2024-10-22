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
package com.android.tools.adtui.toolwindow.splittingtabs

import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.HORIZONTAL
import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.VERTICAL
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsBundle.lazyMessage
import com.android.tools.adtui.toolwindow.splittingtabs.state.PanelState
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A [JPanel] that can split itself in the specified [SplitOrientation].
 *
 * The split is performed by inserting a [OnePixelSplitter] between the component and its parent. The original component is set as the
 * [OnePixelSplitter.setFirstComponent] and a new SplittingPanel is assigned to [OnePixelSplitter.setSecondComponent].
 *
 * Any SplittingPanel in the hierarchy can also be closed. Closing a panel is accomplished by removing the parent OnePixelSplitter and
 * attaching the child SplittingPanel that is not being closed to the hierarchy where the parent was attached to.
 *
 * This code was inspired by `org.jetbrains.plugins.terminal.TerminalContainer`
 */
internal class SplittingPanel(
  private val content: Content,
  clientState: String?,
  private val childComponentFactory: ChildComponentFactory
) : BorderLayoutPanel(), SplittingTabsStateProvider, Disposable {

  private val popupActionGroup = DefaultActionGroup(
    SplitPanelAction(VERTICAL),
    SplitPanelAction(HORIZONTAL),
    ClosePanelAction())

  val component = childComponentFactory.createChildComponent(clientState, popupActionGroup)

  init {
    addToCenter(component)
    Disposer.register(content, this)
    if (component is Disposable) {
      Disposer.register(this, component)
    }
  }

  override fun dispose() {}

  fun split(orientation: SplitOrientation) {
    val parent = parent
    val splitter = createSplitter(orientation, this, SplittingPanel(content, clientState = null, childComponentFactory))

    if (parent is OnePixelSplitter) {
      if (parent.firstComponent == this) {
        parent.firstComponent = splitter
      }
      else {
        parent.secondComponent = splitter
      }
    }
    else {
      parent.remove(this)
      parent.add(splitter)
      content.component = splitter
    }
    content.manager?.setSelectedContent(content)

    parent.revalidate()
  }

  // TODO(aalbert): Make this private if we are able to test close through the popup menu.
  @VisibleForTesting
  fun close() {
    val parent = parent
    if (parent is OnePixelSplitter) {
      val grandparent = parent.parent
      val other = if (parent.firstComponent == this) parent.secondComponent else parent.firstComponent
      if (grandparent is OnePixelSplitter) {
        if (grandparent.firstComponent == parent) {
          grandparent.firstComponent = other
        }
        else {
          grandparent.secondComponent = other
        }
      }
      else {
        grandparent.remove(parent)
        grandparent.add(other)
        content.component = other
      }
      Disposer.dispose(this)
      grandparent.revalidate()
    }
    else {
      parent.remove(this)
      content.manager?.removeContent(content, true)
    }
  }

  private fun createSplitter(orientation: SplitOrientation, first: SplittingPanel, second: SplittingPanel): OnePixelSplitter {
    return OnePixelSplitter(orientation.toSplitter(), 0.5f, 0.1f, 0.9f).apply {
      firstComponent = first
      secondComponent = second
      dividerWidth = JBUI.scale(1)
      val scheme = EditorColorsManager.getInstance().globalScheme
      val color = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
      if (color != null) {
        divider.background = color
      }
    }
  }

  override fun getState(): String? = (component as? SplittingTabsStateProvider)?.getState()

  private inner class SplitPanelAction(private val orientation: SplitOrientation) : DumbAwareAction(orientation::text, orientation.icon) {
    override fun actionPerformed(e: AnActionEvent) {
      split(orientation)
    }
  }

  private inner class ClosePanelAction : DumbAwareAction(lazyMessage("SplittingTabsToolWindow.close"), AllIcons.Actions.Close) {
    override fun actionPerformed(e: AnActionEvent) {
      close()
    }
  }

  companion object {
    /**
     * Recursively traverses hierarchy until a [SplittingPanel] is found.
     */
    internal fun findFirstSplitter(component: JComponent): SplittingPanel? =
      when (component) {
        is SplittingPanel -> component
        is OnePixelSplitter -> findFirstSplitter(component.firstComponent)
        else -> null
      }

    /**
     * Recursively builds a [PanelState] from a component hierarchy.
     */
    internal fun buildStateFromComponent(component: JComponent): PanelState =
      if (component is Splitter) {
        PanelState(
          orientation = SplitOrientation.fromSplitter(component),
          proportion = component.proportion,
          first = buildStateFromComponent(component.firstComponent),
          second = buildStateFromComponent(component.secondComponent))
      }
      else {
        PanelState(clientState = (component as? SplittingTabsStateProvider)?.getState())
      }

    /**
     * Recursively builds a component hierarchy from a [PanelState].
     */
    internal fun buildComponentFromState(
      content: Content,
      panelState: PanelState?,
      childComponentFactory: ChildComponentFactory
    ): JComponent =
      when {
        panelState == null -> SplittingPanel(content, clientState = null, childComponentFactory)
        panelState.isLeaf() -> SplittingPanel(content, panelState.clientState, childComponentFactory)
        else -> {
          OnePixelSplitter(panelState.orientation!!.toSplitter(), panelState.proportion!!).also {
            it.firstComponent = buildComponentFromState(content, panelState.first!!, childComponentFactory)
            it.secondComponent = buildComponentFromState(content, panelState.second!!, childComponentFactory)
          }
        }
      }
  }
}

