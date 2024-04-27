/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering.actions

import com.android.tools.rendering.RenderService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.Nls
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.random.Random
import kotlin.reflect.KMutableProperty0

/**
 * An internal action to put the rendering pipeline into a "lagging mode". This will create slow rendering requests
 * to slow down the rendering pipeline.
 * This can be used to test how other components of the rendering (Layout Editor, Compose Preview, Resource Manager, RenderTask)
 * react to a heavy loaded queue. This can be used to verify that the code from those components can gracefully handle timeouts or
 * slow renderings correctly.
 */
class RenderLaggingMode : ToggleAction("Enable Layout Preview Lagging Mode") {
  private val LOG = Logger.getInstance(RenderLaggingMode::class.java)

  private var enabled = false

  /** Minimum time for a request to execute in the rendering thread. */
  private var minWaitTimeMs = TimeUnit.SECONDS.toMillis(2).toInt()
    set(value) { field = value.coerceAtMost(maxWaitTimeMs) }

  /** Maximum time for a request to execute in the rendering thread. */
  private var maxWaitTimeMs = TimeUnit.SECONDS.toMillis(4).toInt()
    set(value) { field = value.coerceAtLeast(minWaitTimeMs)}

  /**
   * Minimum time to space requests creation. If this is smaller than [minWaitTimeMs], requests will accumulate in the queue and will
   * eventually cause the queue to become full.
   */
  private var minSpaceTimeMs = 500
    set(value) { field = value.coerceAtMost(maxSpaceTimeMs) }

  /** Max time between two wait requests. */
  private var maxSpaceTimeMs = TimeUnit.SECONDS.toMillis(5).toInt()
    set(value) { field = value.coerceAtLeast(minSpaceTimeMs)}

  /**
   * The [lagger] method will wait on this lock to do its sleep cycles. When the action is disabled, the lock will be released
   * so all the waits will immediately be cancelled.
   */
  private val sleepLock = ReentrantReadWriteLock()
  private val lagger = {
    if (enabled) {
      RenderService.getRenderAsyncActionExecutor().run {
        val waitTimeMs = Random.nextLong(minWaitTimeMs.toLong(), maxWaitTimeMs.toLong())
        LOG.info("Waiting ${waitTimeMs}ms")
        if (!sleepLock.readLock().tryLock(waitTimeMs, TimeUnit.MILLISECONDS)) {
          LOG.info("Waited ${waitTimeMs}ms")
        }
      }
      reschedule()
    }
  }

  private fun reschedule() {
    if (enabled) {
      val spaceTimeMs = Random.nextLong(minSpaceTimeMs.toLong(), maxSpaceTimeMs.toLong())
      LOG.info("Request scheduled in ${spaceTimeMs}ms")
      AppExecutorUtil.getAppScheduledExecutorService().schedule(lagger, spaceTimeMs, TimeUnit.MILLISECONDS)
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean = enabled
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (enabled) {
      enabled = false
      sleepLock.writeLock().unlock()
    }
    else {
      val builder = DialogBuilder()
      builder.setCenterPanel(panel {
        spinnerParamMs("Min wait time", ::minWaitTimeMs)
        spinnerParamMs("Max wait time", ::maxWaitTimeMs)
        spinnerParamMs("Min action space time", ::minSpaceTimeMs)
        spinnerParamMs("Max action space time", ::maxSpaceTimeMs)
      })
      builder.setOkOperation {
        enabled = true
        sleepLock.writeLock().lock()
        reschedule()
        builder.dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
      }
      builder.show()
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

}

private fun Panel.spinnerParamMs(@Nls label: String, prop: KMutableProperty0<Int>) {
  row(label) {
    spinner(0..90000)
      .bindIntValue(prop)
      .gap(RightGap.SMALL)
    label("ms")
  }
}
