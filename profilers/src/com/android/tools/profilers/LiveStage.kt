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
package com.android.tools.profilers;

import com.android.tools.profilers.cpu.LiveCpuUsageModel
import com.android.tools.profilers.event.EventMonitor
import com.android.tools.profilers.memory.LiveMemoryFootprintModel
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import org.jetbrains.annotations.NotNull
import java.util.Optional

class LiveStage(@NotNull private val profilers : StudioProfilers) : StreamingStage(profilers) {

  @NotNull
  val liveModels = mutableListOf<LiveDataModel>()

  @NotNull
  var eventMonitor:Optional<EventMonitor> = Optional.empty()

  override fun enter() {
    logEnterStage()
    eventMonitor.ifPresent(EventMonitor::enter)

    // Clear the selection
    timeline.selectionRange.clear()
    liveModels.clear()

    eventMonitor = Optional.ofNullable(getEventMonitorInstance())
    liveModels.add(LiveCpuUsageModel(profilers, this))
    liveModels.add(LiveMemoryFootprintModel(profilers))

    liveModels.stream().forEach { liveModel -> liveModel.enter() }
    profilers.ideServices.featureTracker.trackEnterStage(stageType)
  }

  override fun exit() {
    liveModels.clear()
    liveModels.stream().forEach { liveModel -> liveModel.exit() }
    eventMonitor.ifPresent(EventMonitor::exit)
  }

  private fun getEventMonitorInstance() =
    if (profilers.selectedSessionSupportLevel == SupportLevel.DEBUGGABLE) EventMonitor(profilers) else null

  override fun getStageType(): AndroidProfilerEvent.Stage = AndroidProfilerEvent.Stage.LIVE_STAGE
}


