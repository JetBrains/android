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
package com.android.tools.idea.editors.literals.actions

import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.editors.literals.internal.DeployRecordStats
import com.android.tools.idea.editors.literals.internal.LiveLiteralsDiagnosticsManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.BorderLayout
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.random.Random

private const val FAKE_DEVICE_ID = "internal_fake_device"
private val pushIdCounter = AtomicLong(0)

private fun simulateDeployment(project: Project, problems: Collection<LiveLiteralsMonitorHandler.Problem> = listOf()) {
  val randomTimeMs = Random.nextInt(100, 1200).toLong()
  val pushId = pushIdCounter.getAndIncrement().toString(16)

  LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(FAKE_DEVICE_ID, LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
  LiveLiteralsService.getInstance(project).liveLiteralPushStarted(FAKE_DEVICE_ID, pushId)
  AppExecutorUtil.getAppScheduledExecutorService().schedule(Callable {
    LiveLiteralsService.getInstance(project).liveLiteralPushed(FAKE_DEVICE_ID, pushId, problems)
  }, randomTimeMs, TimeUnit.MILLISECONDS)
}

/**
 * Allows simulating a fake live literals successful deployment. This can be used for testing the UI when no device is available
 * running Live Literals.
 */
@Suppress("ComponentNotRegistered")
internal class InternalSimulateSuccessfulLiteralDeployment: AnAction("Simulate Successful Live Literal Deployment") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    simulateDeployment(project)
  }
}

/**
 * Allows simulating a fake live literals failed deployment. This can be used for testing the UI when no device is available
 * running Live Literals.
 */
@Suppress("ComponentNotRegistered")
internal class InternalSimulateFailedLiteralDeployment: AnAction("Simulate Failed Live Literal Deployment") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    simulateDeployment(project, listOf(LiveLiteralsMonitorHandler.Problem.error("Failed to deploy")))
  }
}

/**
 * Dialog displaying the given string.
 */
private class ShowReportDialog(content: String) : DialogWrapper(false) {
  private val textArea: JTextArea = JTextArea(30, 130).apply {
    text = content
    isEditable = false
    caretPosition = 0
  }

  init {
    title = "Live Literals Deployment Stats"
    isModal = true
    init()
  }

  override fun createActions(): Array<Action> = arrayOf(okAction)

  override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
    add(JBScrollPane(textArea), BorderLayout.CENTER)
  }
}

/**
 * Action that shows a dialog with the latest collected Live Literals stats.
 */
@Suppress("ComponentNotRegistered")
internal class ShowLiteralStats: AnAction("Show Live Literals Stats") {
  private fun DeployRecordStats.toDebugString(title: String) = """
$title

90th percentile = ${deployTime(90)}ms
99th percentile = ${deployTime(99)}ms
avg = ${deployTime(50)}ms

last deployments ms = [${lastDeploymentTimesMs().joinToString { "${it}ms" }}]
"""

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val diag = LiveLiteralsDiagnosticsManager.getReadInstance(project)
    val deviceIds = diag.lastRecordedDevices()
    val perDevice = if (deviceIds.size > 1) {
      """
        ${
        deviceIds.joinToString("\n\n") {
          diag.lastDeploymentStatsForDevice(it).toDebugString("== Stats for device $it ===")
        }
      }
      """.trimIndent()
    }
    else ""
    val content = """
      total successful = ${diag.successfulDeploymentCount()}
      total failed = ${diag.failedDeploymentCount()}

      ${diag.lastDeploymentStats().toDebugString("== Stats for all devices ===")}

      $perDevice
    """.trimIndent()
    ShowReportDialog(content).show()
  }
}