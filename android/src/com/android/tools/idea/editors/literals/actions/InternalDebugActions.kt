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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val FAKE_DEVICE_ID = "internal_fake_device"

private fun simulateDeployment(project: Project, problems: Collection<LiveLiteralsMonitorHandler.Problem> = listOf()) {
  val randomTimeMs = Random.nextInt(100, 1200).toLong()

  LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(FAKE_DEVICE_ID)
  AppExecutorUtil.getAppScheduledExecutorService().schedule(Callable {
    LiveLiteralsService.getInstance(project).liveLiteralPushed(FAKE_DEVICE_ID, problems)
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