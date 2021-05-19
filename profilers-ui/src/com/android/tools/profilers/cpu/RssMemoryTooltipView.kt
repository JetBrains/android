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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.model.formatter.NumberFormatter
import com.android.tools.profilers.cpu.systemtrace.RssMemoryTooltip
import com.google.common.annotations.VisibleForTesting
import javax.swing.JComponent
import javax.swing.JPanel

class RssMemoryTooltipView(parent: JComponent, val tooltip: RssMemoryTooltip) : TooltipView(tooltip.timeline) {
  private val content = JPanel(TabularLayout("*").setVGap(12))

  @VisibleForTesting
  val descriptionLabel = createTooltipLabel()

  @VisibleForTesting
  val valueLabel = createTooltipLabel()

  override fun createTooltip(): JComponent {
    return content
  }

  private fun updateView() {
    descriptionLabel.text = "<html>${getDescriptionText(tooltip.counterName)}</html>"
    val rssValueText = NumberFormatter.formatFileSize(tooltip.activeValueBytes)
    valueLabel.text = "${getTitle(tooltip.counterName)}: $rssValueText"
  }

  private fun getTitle(counterName: String) =
    when (counterName) {
      "mem.rss" -> "Total Resident Memory Utilization"
      "mem.rss.anon" -> "Resident Memory Allocations"
      "mem.rss.file" -> "Resident File Mapping Memory"
      "mem.rss.shmem" -> "Resident Shared Memory"
      else -> "Value"
    }

  private fun getDescriptionText(counterName: String) =
    when (counterName) {
      "mem.rss" -> "The total of all physical memory in use by the process,<br>" +
                   "including allocations, file mappings and shared memory.<br><br>" +
                   "/proc/&lt;pid&gt;/status reports this value as \"VmRSS\"."
      "mem.rss.anon" -> "The amount of physical memory the process is using for normal<br>" +
                        "memory allocations (those backed by the swap file, and that<br>" +
                        "are not shared).<br><br>" +
                        "/proc/&lt;pid&gt;/status reports this value as \"RssAnon\"."
      "mem.rss.file" -> "The amount of physical memory the process is using for file mappings -<br>" +
                        "that is, memory which is used for files that have been mapped into<br>" +
                        "a region of memory by the memory manager.<br><br>" +
                        "/proc/&lt;pid&gt;/status reports this value as \"RssFile\""
      "mem.rss.shmem" -> "The amount of physical memory the process is using for interprocess sharing.<br><br>" +
                         "/proc/&lt;pid&gt;/status reports this value as \"RssShmem\"."
      else -> ""
    }

  init {
    content.add(valueLabel, TabularLayout.Constraint(0, 0))
    content.add(descriptionLabel, TabularLayout.Constraint(1, 0))
    tooltip.addDependency(this).onChange(RssMemoryTooltip.Aspect.VALUE_CHANGED) { updateView() }
    updateView()
  }
}