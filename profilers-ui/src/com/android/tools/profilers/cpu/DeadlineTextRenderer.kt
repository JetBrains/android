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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.adtui.trackgroup.TrackRenderer
import com.android.tools.profilers.cpu.FrameTimelineSelectionOverlayPanel.GrayOutMode
import com.android.tools.profilers.cpu.systemtrace.DeadlineTextModel
import java.util.function.BooleanSupplier
import javax.swing.JPanel

class DeadlineTextRenderer(private val vsyncEnabler: BooleanSupplier): TrackRenderer<DeadlineTextModel> {
  override fun render(trackModel: TrackModel<DeadlineTextModel, *>) = JPanel().let { content ->
    val model = trackModel.dataModel
    VsyncPanel.of(FrameTimelineSelectionOverlayPanel.of(content,
                                                        model.viewRange,
                                                        model.multiSelectionModel,
                                                        GrayOutMode.None, true, "Deadline"),
                  model.vsyncSeries, vsyncEnabler)
  }
}