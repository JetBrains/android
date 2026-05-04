/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers.capture.unified

import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.idea.profilers.IntellijProfilerServices
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto.ProfilerTaskMetadata
import com.android.tools.profilers.ProfilerContext
import com.android.tools.profilers.ProfilerEmptyStateView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCapture
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * An implementation of [ProfilerContext] used for offline/recorded capture files.
 *
 * If parsing fails, it handles presenting the error UI directly inside the provided parent component.
 *
 * @property ideServices Services provided by the IDE for profiling tasks.
 * @property profilers The main [StudioProfilers] instance controlling the profiling state.
 * @property component The UI container where profiler views or error messages are rendered.
 * @property offlineMetadata Optional metadata associated with the offline task.
 */
class OfflineProfilerContext(
  private val ideServices: IntellijProfilerServices,
  private val profilers: StudioProfilers,
  private val component: JPanel,
  private val offlineMetadata: ProfilerTaskMetadata? = null,
) : ProfilerContext {

  override val taskMetadata: ProfilerTaskMetadata?
    get() = offlineMetadata

  override val ideProfilerServices = ideServices

  override val updater = profilers.updater

  override val isJvmtiEnabled = false

  override fun getDataSeries(capture: CpuCapture?) =
    object : DataSeries<Long> {
      override fun getDataForRange(range: Range): List<SeriesData<Long>> = emptyList()
    }

  override fun onParseFailure(message: String) {
    ideServices.mainExecutor.execute {
      component.removeAll()
      val messageView = ProfilerEmptyStateView("Android Profiler", message)
      component.add(messageView, BorderLayout.CENTER)
      component.revalidate()
      component.repaint()
    }
  }

  override fun reportParsedTrace(capture: CpuCapture) {}
}
