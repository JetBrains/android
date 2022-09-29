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
package com.android.tools.idea.device.dialogs

import com.android.tools.idea.device.benchmark.Benchmarker
import com.android.tools.idea.device.benchmark.DeviceAdapter
import com.android.tools.idea.device.benchmark.DeviceMirroringBenchmarkTarget
import com.intellij.CommonBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.not
import java.awt.Component
import java.awt.Point
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.DefaultBoundedRangeModel
import javax.swing.JProgressBar
import kotlin.math.roundToInt
import kotlin.properties.Delegates

/**
 * Dialog that facilitates benchmarking device mirroring.
 *
 * The dialog shows options for benchmarking and also shows progress while the benchmarking is underway.
 * When benchmarking finishes, a dialog displaying results is popped up.
 */
class DeviceMirroringBenchmarkDialog(private val target: DeviceMirroringBenchmarkTarget) {
  private val isRunningListeners: MutableList<(Boolean) -> Unit> = mutableListOf()
  private val isRunning = object : ComponentPredicate() {
      override fun invoke(): Boolean = benchmarker != null

      override fun addListener(listener: (Boolean) -> Unit) {
        isRunningListeners.add(listener)
      }
    }
  private val dispatchedProgressBar = JProgressBar(DefaultBoundedRangeModel(0, 0, 0, 100))
  private val receivedProgressBar = JProgressBar(DefaultBoundedRangeModel(0, 0, 0, 100))

  private var benchmarker: Benchmarker<Point>? by Delegates.observable(null) { _, _, newValue ->
    isRunningListeners.forEach { it(newValue != null) }
  }
  private var touchRateHz = 60
  private var maxTouches = 10_000
  private var step = 1
  private var spikiness = 3
  private var bitsPerChannel = 2
  private var latencyBits = 6

  private fun createPanel() = panel {
    panel {
      row("Input event rate") {
        intTextField(1..240, 5).bindIntText(::touchRateHz)
        text("Hz")
      }
      row("Max input events") {
        intTextField(1..Int.MAX_VALUE, 100).bindIntText(::maxTouches)
      }
      row("Drag speed") {
        intTextField(1 .. 10, 1).bindIntText(::step)
        text("px/frame")
      }
      row("Spikiness") {
        intTextField(0..100, 1).bindIntText(::spikiness)
        text("oscillations/row")
      }
      row("Bits per channel") {
        intTextField(0..8, 1).bindIntText(::bitsPerChannel)
        text("use 0 for monochrome")
      }
      row("Frame latency bits") {
        intTextField(1..16, 1).bindIntText(::latencyBits)
      }
    }.enabledIf(isRunning.not())
    panel {
      separator("Note")
      row {
        text("For accurate results, keep Android Studio visible until benchmarking is complete.").horizontalAlign(HorizontalAlign.CENTER)
      }
      separator("Progress")
      row("Input events dispatched") {
        cell(dispatchedProgressBar)
        button("Cancel") {
          benchmarker?.stop()
        }.enabledIf(isRunning)
      }
      row("Input events returned") {
        cell(receivedProgressBar)
      }
    }.visibleIf(isRunning)
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project? = null, parent: Component? = null): DialogWrapper {
    val dialogPanel = createPanel()
    val startAction = StartBenchmarkAction(project, dialogPanel)
    isRunning.addListener { startAction.isEnabled = !it }
    return dialog(
      title = "Benchmark ${target.name} Mirroring",
      resizable = true,
      panel = dialogPanel,
      project = project,
      parent = parent,
      createActions = { listOf(startAction, CloseDialogAction()) })
  }

  private inner class StartBenchmarkAction(private val project: Project?, private val dialogPanel: DialogPanel) : AbstractAction("Benchmark!") {
    init {
      putValue(DialogWrapper.DEFAULT_ACTION, true)
    }

    override fun actionPerformed(e: ActionEvent?) {
      if (project == null) return
      dialogPanel.apply()
      val deviceAdapter = DeviceAdapter(project, target, bitsPerChannel, latencyBits, maxTouches, step, spikiness)
      benchmarker = Benchmarker(deviceAdapter, touchRateHz).apply {
        addOnProgressCallback { dispatchedProgress, receivedProgress ->
          dispatchedProgressBar.updateProgress(dispatchedProgress)
          receivedProgressBar.updateProgress(receivedProgress)
        }
        addOnStoppedCallback {
          if (!isDone()) {
            ApplicationManager.getApplication().invokeLater { showErrorNotification(failureMsg) }
          }
          benchmarker = null
        }
        addOnCompleteCallback {
          ApplicationManager.getApplication().invokeLater {
            DeviceMirroringBenchmarkResultsDialog(target.name, it).createWrapper(project).show()
          }
        }
        start()
      }
    }

    private fun showErrorNotification(msg: String) {
      NotificationGroupManager.getInstance().getNotificationGroup("DeviceMirrorBenchmarking")
        .createNotification(ERROR_TITLE, msg, NotificationType.ERROR).notify(project)
    }
  }

  private inner class CloseDialogAction : AbstractAction(CommonBundle.getCloseButtonText()) {
    override fun actionPerformed(event: ActionEvent) {
      benchmarker?.stop()
      val wrapper = DialogWrapper.findInstance(event.source as? Component)
      wrapper?.close(DialogWrapper.CLOSE_EXIT_CODE)
    }
  }

  private fun JProgressBar.updateProgress(fraction: Double) {
    val percent = (fraction * 100).roundToInt()
    value = percent
    string = "$percent %"
  }

  companion object {
    private const val ERROR_TITLE = "Benchmarking failed"
  }
}
