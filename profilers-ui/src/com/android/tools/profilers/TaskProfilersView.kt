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
package com.android.tools.profilers

import com.android.tools.adtui.model.ViewBinder
import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.profilers.cpu.CpuCaptureStage
import com.android.tools.profilers.cpu.CpuCaptureStageView
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuProfilerStageViewV2
import com.android.tools.profilers.memory.AllocationStage
import com.android.tools.profilers.memory.AllocationStageView
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MainMemoryProfilerStageViewV2
import com.android.tools.profilers.memory.MemoryCaptureStage
import com.android.tools.profilers.memory.MemoryCaptureStageView
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout
import java.util.function.BiFunction
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A view containing a [StageWithToolbarView] and a [ViewBinder] that binds [Stage]s and [StageView]s.
 */
class TaskProfilersView(override val studioProfilers: StudioProfilers,
                        override val ideProfilerComponents: IdeProfilerComponents,
                        parentDisposable: Disposable) : StudioProfilersView {

  override val stageComponent = JPanel(BorderLayout())
  private val layeredPane = TooltipLayeredPane(stageComponent)
  override val component: JComponent
    get() = layeredPane
  override val stageWithToolbarView: StageWithToolbarView
  override val stageView: StageView<*>?
    get() = stageWithToolbarView.stageView
  private val binder: ViewBinder<TaskProfilersView, Stage<*>, StageView<*>> = ViewBinder()

  init {
    binder.bind(NullMonitorStage::class.java, ::NullMonitorStageView)
    binder.bind(CpuProfilerStage::class.java, ::CpuProfilerStageViewV2)
    binder.bind(CpuCaptureStage::class.java, ::CpuCaptureStageView)
    binder.bind(MainMemoryProfilerStage::class.java, ::MainMemoryProfilerStageViewV2)
    binder.bind(MemoryCaptureStage::class.java, ::MemoryCaptureStageView)
    binder.bind(AllocationStage::class.java, ::AllocationStageView)
    binder.bind(LiveStage::class.java, ::LiveStageView)

    stageWithToolbarView = StageWithToolbarView(studioProfilers,
                                                stageComponent,
                                                ideProfilerComponents,
                                                { stage: Stage<*> -> buildStageView(stage) },
                                                stageComponent)

    Disposer.register(parentDisposable, this)
  }

  override fun dispose() {}

  @VisibleForTesting
  fun <S : Stage<*>?, T : StageView<*>?> bind(clazz: Class<S>, constructor: BiFunction<TaskProfilersView?, S, T>) {
    binder.bind(clazz, constructor)
  }

  private fun buildStageView(stage: Stage<*>): StageView<*> {
    return binder.build(this, stage)
  }

  /**
   * Installs the [ContextMenuItem] common to all profilers.
   */
  override fun installCommonMenuItems(component: JComponent) {
    val contextMenuInstaller = ideProfilerComponents.createContextMenuInstaller()
    ProfilerContextMenu.createIfAbsent(stageComponent).contextMenuItems.forEach(
      Consumer { item: ContextMenuItem? -> contextMenuInstaller.installGenericContextMenu(component, item!!) })
  }
}
