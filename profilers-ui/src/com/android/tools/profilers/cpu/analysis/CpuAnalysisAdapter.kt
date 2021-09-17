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
package com.android.tools.profilers.cpu.analysis

import javax.swing.JComponent

/**
 * Interface for providing a view of the {@link CpuAnalysisModel} (or a list of them)
 */
interface CpuAnalysisAdapter {
  fun make(model: CpuAnalysisModel<*>): List<CpuAnalysisView>
  fun make(models: List<CpuAnalysisModel<*>>): List<CpuAnalysisView> = models.flatMap(::make)
}

/**
 * The view that's faithful to the model's structure
 */
class PlainCpuAnalysisAdapter(private val makeTabView: (CpuAnalysisTabModel<*>) -> JComponent): CpuAnalysisAdapter {
  override fun make(model: CpuAnalysisModel<*>) =
    listOf(CpuAnalysisView(model.name, model.tabModels.makeViews(makeTabView)))
}

/**
 * An alternate view where "Frames" is at the top level
 */
class FramesAtTopCpuAnalysisAdapter(private val makeTabView: (CpuAnalysisTabModel<*>) -> JComponent): CpuAnalysisAdapter {
  override fun make(model: CpuAnalysisModel<*>) =
    model.tabModels.partition { it.tabType == CpuAnalysisTabModel.Type.FRAMES }.let { (frames, rest) ->
      when(val framesTab = frames.firstOrNull()) {
        null -> listOf(CpuAnalysisView(model.name, model.tabModels.makeViews(makeTabView)))
        else -> listOf(
          CpuAnalysisView(model.name, rest.makeViews(makeTabView)),
          CpuAnalysisView(framesTab.tabType.getName(), listOf(CpuAnalysisTabView("All Frames") { makeTabView(framesTab) }))
        )
      }
    }
}

private fun Iterable<CpuAnalysisTabModel<*>>.makeViews(make: (CpuAnalysisTabModel<*>) -> JComponent) =
  map { CpuAnalysisTabView(it.tabType.getName()) { make(it) } }

class CpuAnalysisView(val name: String, val tabs: List<CpuAnalysisTabView>)
class CpuAnalysisTabView(val name: String, val view: () -> JComponent)