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
package com.android.tools.idea.uibuilder.scene

import com.intellij.openapi.Disposable
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update

/** Simple render [Update]s scheduling interface. */
interface RenderingQueue {
  fun queue(update: Update)

  fun deactivate() {}
}

private const val RENDER_DELAY_MS = 10

/**
 * A wrapper around [MergingUpdateQueue] for thread-safe task scheduling. Used for scheduling render
 * calls.
 */
class MergingRenderingQueue(parentDisposable: Disposable) : RenderingQueue {
  private val renderingQueue: MergingUpdateQueue =
    MergingUpdateQueue(
      "android.layout.rendering",
      RENDER_DELAY_MS,
      true,
      null,
      parentDisposable,
      null,
      Alarm.ThreadToUse.POOLED_THREAD,
    )

  init {
    renderingQueue.setRestartTimerOnAdd(true)
  }

  @Synchronized
  override fun deactivate() {
    renderingQueue.cancelAllUpdates()
  }

  @Synchronized
  override fun queue(update: Update) {
    renderingQueue.queue(update)
  }
}
