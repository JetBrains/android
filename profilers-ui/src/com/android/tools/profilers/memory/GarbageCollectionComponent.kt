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
package com.android.tools.profilers.memory

import com.android.tools.adtui.chart.linechart.DurationDataRenderer
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.stdui.DefaultContextMenuItem
import com.android.tools.profilers.ProfilerLayout
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.SupportLevel
import com.android.tools.profilers.memory.adapters.MemoryDataProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke

class GarbageCollectionComponent {
  fun makeGarbageCollectionButton(memoryDataProvider: MemoryDataProvider, studioProfilers: StudioProfilers): CommonButton {
    val myForceGarbageCollectionButton = CommonButton(StudioIcons.Profiler.Toolbar.FORCE_GARBAGE_COLLECTION)
    myForceGarbageCollectionButton.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.FORCE_GARBAGE_COLLECTION)
    myForceGarbageCollectionButton.addActionListener {
      memoryDataProvider.forceGarbageCollection()
      studioProfilers.ideServices.featureTracker.trackForceGc()
    }
    return myForceGarbageCollectionButton
  }

  fun makeGcDurationDataRenderer(detailedMemoryUsage: DetailedMemoryUsage,
                                 tooltipLegends: MemoryStageLegends) =
    DurationDataRenderer.Builder(detailedMemoryUsage.gcDurations, JBColor.BLACK)
      .setIcon(StudioIcons.Profiler.Events.GARBAGE_EVENT)
      // Need to offset the GcDurationData by the margin difference between the overlay component and the
      // line chart. This ensures we are able to render the Gc events in the proper locations on the line.
      .setLabelOffsets(-StudioIcons.Profiler.Events.GARBAGE_EVENT.iconWidth / 2f,
                       StudioIcons.Profiler.Events.GARBAGE_EVENT.iconHeight / 2f)
      .setHostInsets(JBUI.insets(ProfilerLayout.Y_AXIS_TOP_MARGIN, 0, 0, 0))
      .setHoverHandler { tooltipLegends.gcDurationLegend.setPickData(it) }
      .setClickRegionPadding(0, 0)
      .build()

  fun makeGarbageCollectionAction(profilers: StudioProfilers,
                                  myForceGarbageCollectionButton: JButton,
                                  containerComponent: JComponent): DefaultContextMenuItem {
    return DefaultContextMenuItem.Builder(FORCE_GARBAGE_COLLECTION)
      .setContainerComponent(containerComponent)
      .setIcon(myForceGarbageCollectionButton.icon)
      .setActionRunnable { myForceGarbageCollectionButton.doClick(0) }
      .setEnableBooleanSupplier { getGcSupportStatus(profilers).isSupported }
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_G, getActionMask())).build()
  }

  fun getGcSupportStatus(profilers: StudioProfilers): GcSupportStatus {
    return if (!profilers.sessionsManager.isSessionAlive) GcSupportStatus.SESSION_DEAD
    else if (profilers.selectedSessionSupportLevel.isFeatureSupported(SupportLevel.Feature.MEMORY_GC)) GcSupportStatus.ENABLED
    else GcSupportStatus.PROFILEABLE_PROCESS
  }

  fun updateGcButton(profilers: StudioProfilers, myForceGarbageCollectionButton: JButton) {
    val gcSupportStatus = getGcSupportStatus(profilers)
    myForceGarbageCollectionButton.isEnabled = gcSupportStatus.isSupported
    myForceGarbageCollectionButton.toolTipText = gcSupportStatus.message
  }

  enum class GcSupportStatus(val isSupported: Boolean, val message: String) {
    ENABLED(true, FORCE_GARBAGE_COLLECTION),
    SESSION_DEAD(false, "Forcing garbage collection is unavailable for ended sessions"),
    PROFILEABLE_PROCESS(false, "Forcing garbage collection is not supported for profileable processes")
  }

  companion object {
    private const val FORCE_GARBAGE_COLLECTION = "Force garbage collection"
    private fun getActionMask(): Int {
      // Return the appropriate action mask here.
      // Replace this with the actual implementation.
      return 0
    }
  }
}