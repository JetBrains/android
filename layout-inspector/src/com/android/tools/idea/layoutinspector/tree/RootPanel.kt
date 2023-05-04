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

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessListener
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.ui.LayoutInspectorLoadingObserver
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.JComponent

/**
 * Panel responsible for rendering the component tree, state messages and loading state.
 */
class RootPanel(
  parentDisposable: Disposable,
  componentTreePanel: JComponent
) : BorderLayoutPanel() {
  private val isEmbedded get() = LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled
  @VisibleForTesting
  var showProcessNotDebuggableText = false
    private set

  private var layoutInspectorLoadingObserver: LayoutInspectorLoadingObserver? = null

  var layoutInspector: LayoutInspector? = null
    set(value) {
      // clean up listeners
      layoutInspectorLoadingObserver?.destroy()
      layoutInspectorLoadingObserver = null
      field?.foregroundProcessDetection?.removeForegroundProcessListener(foregroundProcessListener)
      // reset value
      showProcessNotDebuggableText = false

      field = value

      // set up new listeners
      if (isEmbedded) {
        value?.foregroundProcessDetection?.addForegroundProcessListener(foregroundProcessListener)
      }
      if (value != null) {
        layoutInspectorLoadingObserver = createLoadingObserver(value)
      }
    }

  /** Used to show a message when the foreground process is not debuggable */
  private val foregroundProcessListener = ForegroundProcessListener { _, _, isDebuggable ->
    if (isDebuggable) {
      showProcessNotDebuggableText = false
      loadingPanel.isVisible = true

      invalidate()
      repaint()
    }
    else {
      showProcessNotDebuggableText = true
      loadingPanel.isVisible = false
      invalidate()
      repaint()
    }
  }

  /** Status text rendered when the foreground process is not debuggable */
  private val processNotDebuggableText = TreeStatusText(
    owner = this,
    lines = listOf(
      LayoutInspectorBundle.message("application.not.inspectable"),
      LayoutInspectorBundle.message("navigate.to.debuggable.application")
    ),
    shouldShow = { showProcessNotDebuggableText && isEmbedded }
  )

  /** Panel used to show a loading indicator */
  private val loadingPanel = JBLoadingPanel(BorderLayout(), parentDisposable, 0)

  init {
    loadingPanel.add(componentTreePanel)
    addToCenter(loadingPanel)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    processNotDebuggableText.paint(this, g)
  }

  private fun createLoadingObserver(layoutInspector: LayoutInspector): LayoutInspectorLoadingObserver {
    val layoutInspectorLoadingObserver = LayoutInspectorLoadingObserver(layoutInspector)
    layoutInspectorLoadingObserver.listeners.add(object : LayoutInspectorLoadingObserver.Listener {
      override fun onStartLoading() {
        loadingPanel.isVisible = true
        showProcessNotDebuggableText = false
        loadingPanel.startLoading()
      }

      override fun onStopLoading() {
        loadingPanel.stopLoading()
      }
    })
    return layoutInspectorLoadingObserver
  }
}

private class TreeStatusText(owner: JComponent, lines: List<String>, private val shouldShow: () -> Boolean) : StatusText(owner) {
  init {
    lines.forEach { appendLine(it) }
  }

  override fun isStatusVisible() = shouldShow()
}