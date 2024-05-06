/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

abstract class PsDaemon protected constructor(protected val parentDisposable: Disposable) : Disposable {
  protected abstract val mainQueue: MergingUpdateQueue
  protected abstract val resultsUpdaterQueue: MergingUpdateQueue
  protected val isStopped: Boolean get() = stopped.get()
  val isRunning: Boolean
    // Note: resultsUpdaterQueue.isFlushing is ok to be true since it happens on EDT.
    @UiThread get() = !mainQueue.isEmpty || mainQueue.isFlushing || !resultsUpdaterQueue.isEmpty

  private val stopped = AtomicBoolean(false)

  init {
    Disposer.register(parentDisposable, @Suppress("LeakingThis") this)
  }

  protected fun createQueue(name: String, modalityStateComponent: JComponent?): MergingUpdateQueue =
    MergingUpdateQueue(name, 300, false, modalityStateComponent, this, null,
                       if (modalityStateComponent != null) Alarm.ThreadToUse.SWING_THREAD else Alarm.ThreadToUse.POOLED_THREAD)

  fun reset() {
    reset(mainQueue, resultsUpdaterQueue)
    mainQueue.queue(object : Update("reset") {
      override fun run() {
        stopped.set(false)
      }
    })
  }

  private fun reset(vararg queues: MergingUpdateQueue) {
    for (queue in queues) {
      queue.activate()
    }
  }

  fun stop() {
    stopped.set(true)
    stop(mainQueue, resultsUpdaterQueue)
  }

  private fun stop(vararg queues: MergingUpdateQueue) {
    for (queue in queues) {
      queue.cancelAllUpdates()
      queue.deactivate()
    }
  }

  override fun dispose() {
    stop()
  }
}
