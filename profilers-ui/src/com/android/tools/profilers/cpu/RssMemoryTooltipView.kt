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
    descriptionLabel.text = "${tooltip.counterName}: ${getDescriptionText(tooltip.counterName)}"
    val rssValueText = NumberFormatter.formatFileSize(tooltip.activeValueBytes)
    valueLabel.text = "Value: $rssValueText"
  }

  private fun getDescriptionText(counterName: String) =
    when (counterName) {
      "mem.rss" -> "Resident set size. Sum of mem.rss.anon, mem.rss.file, and mem.rss.shmem."
      "mem.rss.anon" -> "Size of resident anonymous memory."
      "mem.rss.file" -> "Size of resident file mappings."
      "mem.rss.shmem" -> "Size of resident shared memory."
      else -> "Unknown."
    }

  init {
    content.add(descriptionLabel, TabularLayout.Constraint(0, 0))
    content.add(valueLabel, TabularLayout.Constraint(1, 0))
    tooltip.addDependency(this).onChange(RssMemoryTooltip.Aspect.VALUE_CHANGED) { updateView() }
    updateView()
  }
}