/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.sdklib.devices.Storage
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.IntProperty
import com.android.tools.idea.observable.ui.SliderValueProperty
import com.android.tools.idea.observable.ui.SpinnerValueProperty
import com.android.tools.idea.welcome.install.HAXM_DOCUMENTATION_URL
import com.android.tools.idea.welcome.install.UI_UNITS
import com.android.tools.idea.welcome.install.getMaxHaxmMemory
import com.android.tools.idea.welcome.install.getRecommendedHaxmMemory
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import com.intellij.util.ui.StartupUiUtil
import java.awt.Dimension
import java.awt.Font
import java.util.Hashtable
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.math.abs

/**
 * Wizard page for setting up Intel® HAXM settings
 */
class HaxmInstallSettingsStep(
  private val emulatorMemory: IntProperty
) : ModelWizardStep.WithoutModel("Emulator Settings") {
  private val bindings = BindingsManager()
  private val listeners = ListenerManager()

  private val intelHaxmDocumentationButton = BrowserLink("Intel® HAXM Documentation", HAXM_DOCUMENTATION_URL)

  private val fullMemorySize = AvdManagerConnection.getMemorySize()
  private val maxMemory = getMaxHaxmMemory(fullMemorySize)
  private val recommendedMemorySize = getRecommendedHaxmMemory(fullMemorySize)
  private val ticks = MINOR_TICKS.coerceAtMost(maxMemory / MAX_TICK_RESOLUTION)

  private val memorySizeSpinner = JBIntSpinner(recommendedMemorySize, MIN_EMULATOR_MEMORY, maxMemory, maxMemory / ticks)

  // TODO(qumeric): add properties
  private val memorySlider = JSlider().apply {
    minimumSize = Dimension(200, 20)
    preferredSize = Dimension(750, 50)
    // Empty border is needed to avoid clipping long first and/or last label
    border = BorderFactory.createEmptyBorder(0, 30, 0, 30)
    minimum = MIN_EMULATOR_MEMORY
    maximum = maxMemory
    minorTickSpacing = maxMemory / ticks
    majorTickSpacing = maxMemory / MAJOR_TICKS
  }

  private val panel = panel {
    row {
      label("We have detected that your system can run the Android emulator in an accelerated performance mode.")
    }
    row {
      label(
        "Set the maximum amount of RAM available for the Intel® Hardware Accelerated Execution Manager (HAXM)\n" +
        "to use for all x86 emulator instances. You can change these settings at any time by running the Intel® HAXM\n" +
        "installer."
      )
    }
    row {
      cell {
        label("Refer to the")
        intelHaxmDocumentationButton()
        label("for more information")
      }
    }
    row {
      cell {
        memorySlider()
      }
    }
    row {
      right {
        label("Ram allocation:")
        memorySizeSpinner()
        label(UI_UNITS.toString())
        button("Use recommended size") {
          emulatorMemory.set(recommendedMemorySize)
        }
      }
    }
  }

  init {
    bindings.bindTwoWay(SpinnerValueProperty(memorySizeSpinner), emulatorMemory)
    bindings.bindTwoWay(SliderValueProperty(memorySlider), emulatorMemory)

    // TODO(qumeric): ticks are not being shown for some reason. Maybe we don't really need them a
    val displayUnit = getMemoryDisplayUnit(maxMemory * UI_UNITS.numberOfBytes)
    val labels = Hashtable<Int, JLabel>()
    val labelSpacing = ((maxMemory - MIN_EMULATOR_MEMORY) / MAJOR_TICKS).coerceAtLeast(1)
    // Avoid overlapping
    val minDistanceBetweenLabels = labelSpacing / 3
    var i = maxMemory
    while (i >= labelSpacing) {
      if (abs(i - recommendedMemorySize) > minDistanceBetweenLabels && i - MIN_EMULATOR_MEMORY > minDistanceBetweenLabels) {
        labels[i] = JLabel(getMemoryLabel(i, displayUnit))
      }
      i -= labelSpacing
    }

    if (recommendedMemorySize - MIN_EMULATOR_MEMORY > minDistanceBetweenLabels) {
      labels[MIN_EMULATOR_MEMORY] = JLabel(getMemoryLabel(MIN_EMULATOR_MEMORY, UI_UNITS))
    }
    labels[recommendedMemorySize] = createRecommendedSizeLabel(recommendedMemorySize, displayUnit)
    memorySlider.labelTable = labels
  }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusComponent(): JComponent? = memorySlider

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
  }
}

private const val MAJOR_TICKS = 4
private const val MINOR_TICKS = 512
// Smallest adjustment user will be able to make with a slider (if the RAM size is small enough)
private const val MAX_TICK_RESOLUTION = 64 //Mb
private const val MIN_EMULATOR_MEMORY = 512 //Mb

private fun getMemoryDisplayUnit(memorySizeBytes: Long) =
  Storage.Unit.values()
    .filter { memorySizeBytes / it.numberOfBytes >= 1 }
    .maxByOrNull { it.numberOfBytes } ?: Storage.Unit.B

private fun createRecommendedSizeLabel(memorySize: Int, displayUnit: Storage.Unit): JLabel {
  val label = getMemoryLabel(memorySize, displayUnit)
  val labelText = "<html><center>$label<br>(Recommended)<center></html>"
  // This is the only way as JSlider resets label font.
  return JBLabel(labelText).apply {
    font = StartupUiUtil.getLabelFont().deriveFont(Font.BOLD)
  }
}

private fun getMemoryLabel(memorySize: Int, displayUnit: Storage.Unit): String {
  val totalMemBytes = memorySize * UI_UNITS.numberOfBytes
  val memBytesInUnits = totalMemBytes.toFloat() / displayUnit.numberOfBytes
  return "$memBytesInUnits ${displayUnit.displayValue}"
}
