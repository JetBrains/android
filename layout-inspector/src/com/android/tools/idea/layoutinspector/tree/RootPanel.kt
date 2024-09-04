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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.adtui.stdui.StandardColors
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessListener
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.ui.AttachProgressProvider
import com.android.tools.idea.layoutinspector.ui.LayoutInspectorLoadingObserver
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.htmlComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Panel responsible for rendering the component tree, state messages and loading state.
 *
 * @param componentTreePanel The component containing the tree panel.
 */
class RootPanel(
  private val parentDisposable: Disposable,
  private val componentTreePanel: JComponent,
) : BorderLayoutPanel(), Disposable {
  private val isEmbedded
    get() = LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled

  private var layoutInspectorLoadingObserver: LayoutInspectorLoadingObserver? = null
  private val connectionListener: (InspectorClient?) -> Unit = { inspectorClient ->
    if (inspectorClient == null || inspectorClient == DisconnectedClient) {
      updateUiState(UiState.WAITING_TO_CONNECT)
    } else if (inspectorClient.isConnected) {
      // We are connected, show the component tree
      updateUiState(UiState.SHOW_TREE)
    }
  }

  override fun dispose() {
    removeListeners()
  }

  private val attachProgressProvider = AttachProgressProvider { loadingPanel.setLoadingText(it) }

  var layoutInspector: LayoutInspector? = null
    set(value) {
      // Clean up before removing old instance of Layout Inspector.
      removeListeners()
      // Reset UI.
      updateUiState(UiState.WAITING_TO_CONNECT)
      layoutInspector?.inspectorModel?.removeAttachStageListener(attachProgressProvider)

      field = value

      if (value == null) {
        return
      }

      // Set up new listeners.
      layoutInspectorLoadingObserver = createLoadingObserver(value)
      value.inspectorModel.addConnectionListener(connectionListener)
      if (isEmbedded) {
        // Add the listener only if we're running in Embedded Layout Inspector.
        // Outside of Layout Inspector the "app not debuggable" indicator is in the main panel.
        value.foregroundProcessDetection?.addForegroundProcessListener(foregroundProcessListener)
      }

      value.inspectorModel.addAttachStageListener(attachProgressProvider)
    }

  private val foregroundProcessListener = ForegroundProcessListener { _, _, isDebuggable ->
    if (layoutInspector?.currentClient?.isConnected == true) {
      // We got a foreground process event, but the current client is connected
      return@ForegroundProcessListener
    }

    if (isDebuggable) {
      updateUiState(UiState.WAITING_TO_CONNECT)
    } else {
      updateUiState(UiState.PROCESS_NOT_DEBUGGABLE)
    }
  }

  /** Panel shown when no other panel should be shown . */
  private val defaultPanel =
    createCenterTextPanel(listOf("Waiting for Layout Inspector to connect."))
  /** Panel used to show a loading indicator. */
  private val loadingPanel = JBLoadingPanel(BorderLayout(), parentDisposable, 0)
  /** Panel used to indicate that the current foreground process is not debuggable. */
  private val processNotDebuggablePanel =
    createCenterTextPanel(
      listOf(
        LayoutInspectorBundle.message("application.not.inspectable"),
        LayoutInspectorBundle.message("navigate.to.debuggable.application"),
      )
    )

  init {
    Disposer.register(parentDisposable, this)
    updateUiState(UiState.WAITING_TO_CONNECT)
  }

  /** The set of possible states the UI can be in */
  enum class UiState {
    PROCESS_NOT_DEBUGGABLE,
    SHOW_TREE,
    WAITING_TO_CONNECT,
    START_LOADING,
  }

  @VisibleForTesting
  var uiState: UiState? = null
    private set

  private fun removeListeners() {
    layoutInspector?.inspectorModel?.removeConnectionListener(connectionListener)
    layoutInspector
      ?.foregroundProcessDetection
      ?.removeForegroundProcessListener(foregroundProcessListener)
  }

  /**
   * Update the state of the UI. Each time this method is called, all components are removed from
   * the UI and the desired component is added. Only one component at a time should be visible.
   */
  private fun updateUiState(newUiState: UiState) = invokeLater {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (uiState == newUiState) {
      return@invokeLater
    }
    uiState = newUiState

    remove(defaultPanel)
    remove(componentTreePanel)
    remove(processNotDebuggablePanel)
    remove(loadingPanel)

    when (newUiState) {
      UiState.PROCESS_NOT_DEBUGGABLE -> addToCenter(processNotDebuggablePanel)
      UiState.SHOW_TREE -> addToCenter(componentTreePanel)
      UiState.WAITING_TO_CONNECT -> addToCenter(defaultPanel)
      UiState.START_LOADING -> {
        addToCenter(loadingPanel)
        loadingPanel.startLoading()
      }
    }

    revalidate()
    repaint()
  }

  private fun createLoadingObserver(
    layoutInspector: LayoutInspector
  ): LayoutInspectorLoadingObserver {
    val layoutInspectorLoadingObserver =
      LayoutInspectorLoadingObserver(parentDisposable, layoutInspector)
    layoutInspectorLoadingObserver.listeners.add(
      object : LayoutInspectorLoadingObserver.Listener {
        override fun onStartLoading() {
          updateUiState(UiState.START_LOADING)
        }

        override fun onStopLoading() {
          // Never stop loading, the loading panel will be removed automatically when the UI is
          // updated.
        }
      }
    )
    return layoutInspectorLoadingObserver
  }
}

/**
 * Creates a panel containing text centered vertically and horizontally. The text is wrapped if it
 * doesn't fit in its container.
 *
 * @param lines Each string is shown on a separate line.
 */
fun createCenterTextPanel(lines: List<String>): JPanel {
  val html =
    """
  <center>
  ${ lines.joinToString(prefix = "<p>", postfix = "</p>", separator = "<p/>") { it } }
  </center>
  """
      .trimIndent()

  val text =
    htmlComponent(
      text = html,
      lineWrap = true,
      font = JBUI.Fonts.label(13f),
      foreground = StandardColors.PLACEHOLDER_TEXT_COLOR,
    )

  text.isOpaque = false
  text.isFocusable = false
  text.border = JBUI.Borders.empty()

  // To center vertically
  val panel = JPanel(GridBagLayout())
  val constraints =
    GridBagConstraints().apply {
      fill = GridBagConstraints.BOTH
      gridx = 0
      gridy = 0
      weightx = 1.0
    }

  panel.add(text, constraints)
  return panel
}
