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
package com.android.tools.profilers.memory

import com.android.tools.adtui.model.Timeline
import com.android.tools.profilers.Stage
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.adapters.CaptureObject
import java.util.concurrent.Executor

class HeapDumpStage(profilers: StudioProfilers,
                    loader: CaptureObjectLoader,
                    private val durationData: CaptureDurationData<out CaptureObject?>?,
                    private val joiner: Executor?)
      : BaseMemoryProfilerStage(profilers, loader) {

  override fun enter() {
    studioProfilers.ideServices.featureTracker.trackEnterStage(javaClass)
    loader.start()
    doSelectCaptureDuration(durationData, joiner)
  }
  override fun exit() {
    loader.stop()
  }

  override fun getParentStage() = MemoryProfilerStage(studioProfilers, loader)
  override fun getHomeStageClass() = MemoryProfilerStage::class.java
  override fun isInteractingWithTimeline() = false
}