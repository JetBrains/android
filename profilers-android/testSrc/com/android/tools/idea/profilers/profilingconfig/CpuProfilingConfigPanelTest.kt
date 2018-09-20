/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.profilers.profilingconfig

import com.android.flags.junit.RestoreFlagRule
import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.profiler.CpuProfilerConfig
import com.android.tools.profilers.cpu.ProfilingConfiguration
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import javax.swing.*


class CpuProfilingConfigPanelTest {
  @get:Rule public val myRestoreFlagRule = RestoreFlagRule(StudioFlags.PROFILER_SAMPLE_LIVE_ALLOCATIONS)

  private lateinit var myConfigPanel: CpuProfilingConfigPanel
  private lateinit var myConfiguration: ProfilingConfiguration

  @Before
  fun setUp() {
    myConfigPanel = CpuProfilingConfigPanel(AndroidVersion.VersionCodes.O)
    myConfiguration = ProfilingConfiguration()
    myConfigPanel.setConfiguration(myConfiguration, false)
  }

  @Test
  fun testFileSizeLimitDisplayWhenValueChanges() {
    val treeWalker = TreeWalker(myConfigPanel.component)
    val fileSize = treeWalker.descendants().filterIsInstance<JSlider>().first()
    val fileSizeLimit = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == String.format("%d MB", ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
    }

    assertThat(fileSize.value).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
    assertThat(fileSizeLimit.text).isEqualTo(String.format("%d MB", ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB))

    fileSize.value = 1024
    assertThat(fileSizeLimit.text).isEqualTo("1.00 GB")

    fileSize.value = 2345
    assertThat(fileSizeLimit.text).isEqualTo(String.format("%.2f GB", 2345 / 1024.0))
  }

  @Test
  fun testFileSizeLimitValueSet() {
    val fileSize = TreeWalker(myConfigPanel.component).descendants().filterIsInstance<JSlider>().first()
    assertThat(myConfiguration.profilingBufferSizeInMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)

    fileSize.value = 1024
    assertThat(myConfiguration.profilingBufferSizeInMb).isEqualTo(1024)

    fileSize.value = 2345
    assertThat(myConfiguration.profilingBufferSizeInMb).isEqualTo(2345)
  }

  @Test
  fun testSamplingIntervalValueSet() {
    val samplingInterval = TreeWalker(myConfigPanel.component).descendants().filterIsInstance<JSpinner>().first()
    assertThat(myConfiguration.profilingSamplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)

    samplingInterval.model.value = 123
    assertThat(myConfiguration.profilingSamplingIntervalUs).isEqualTo(123)

    samplingInterval.model.value = 456789
    assertThat(myConfiguration.profilingSamplingIntervalUs).isEqualTo(456789)
  }

  @Test
  fun testDisableLiveAllocationValueSet() {
    assertThat(myConfiguration.isDisableLiveAllocation).isTrue()

    val disableLiveAllocation = TreeWalker(myConfigPanel.component).descendants().filterIsInstance<JCheckBox>().first()
    disableLiveAllocation.isSelected = false
    assertThat(myConfiguration.isDisableLiveAllocation).isFalse()
  }

  @Test
  fun testUsingDefaultConfiguration() {
    myConfigPanel.setConfiguration(ProfilingConfiguration(), true)

    val treeWalker = TreeWalker(myConfigPanel.component)
    assertThat(myConfigPanel.preferredFocusComponent.isEnabled).isFalse()

    val artSampledButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.SAMPLED_JAVA.getName()
    }
    val artInstrumentedButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.INSTRUMENTED_JAVA.getName()
    }
    val simpleperfButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.SAMPLED_NATIVE.getName()
    }

    assertThat(artSampledButton.isEnabled).isFalse()
    assertThat(artInstrumentedButton.isEnabled).isFalse()
    assertThat(simpleperfButton.isEnabled).isFalse()

    val fileSize = treeWalker.descendants().filterIsInstance<JSlider>().first()
    val fileSizeLimit = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == String.format("%d MB", ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
    }
    val fileSizeLimitText = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == CpuProfilingConfigPanel.FILE_SIZE_LIMIT
    }

    assertThat(fileSize.isEnabled).isFalse()
    assertThat(fileSizeLimit.isEnabled).isFalse()
    assertThat(fileSizeLimitText.isEnabled).isFalse()

    val samplingInterval = treeWalker.descendants().filterIsInstance<JSpinner>().first()
    val samplingIntervalText = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == CpuProfilingConfigPanel.SAMPLING_INTERVAL
    }
    val samplingIntervalUnit = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == CpuProfilingConfigPanel.SAMPLING_INTERVAL_UNIT
    }

    assertThat(samplingInterval.isEnabled).isFalse()
    assertThat(samplingIntervalText.isEnabled).isFalse()
    assertThat(samplingIntervalUnit.isEnabled).isFalse()

    val disableLiveAllocation = treeWalker.descendants().filterIsInstance<JCheckBox>().first()
    val disableLiveAllocationText = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == CpuProfilingConfigPanel.DISABLE_LIVE_ALLOCATION
    }

    assertThat(disableLiveAllocation.isEnabled).isFalse()
    assertThat(disableLiveAllocationText.isEnabled).isFalse()
  }

  @Test
  fun testLoadingConfiguration() {
    val configuration = ProfilingConfiguration()
    configuration.profilingBufferSizeInMb = 1234
    configuration.profilingSamplingIntervalUs = 56789
    configuration.isDisableLiveAllocation = true

    myConfigPanel.setConfiguration(configuration, false)

    val treeWalker = TreeWalker(myConfigPanel.component)
    val fileSize = treeWalker.descendants().filterIsInstance<JSlider>().first()
    val fileSizeLimit = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == String.format("%.2f GB", 1234 / 1024.0)
    }

    assertThat(fileSize.value).isEqualTo(1234)
    assertThat(fileSizeLimit.text).isEqualTo(String.format("%.2f GB", 1234 / 1024.0))

    val samplingInterval = treeWalker.descendants().filterIsInstance<JSpinner>().first()
    assertThat(samplingInterval.model.value).isEqualTo(56789)

    val disableLiveAllocation = treeWalker.descendants().filterIsInstance<JCheckBox>().first()
    assertThat(disableLiveAllocation.isSelected).isTrue()
  }

  @Test
  fun testResetConfiguration() {
    myConfigPanel.setConfiguration(null, false)

    assertThat((myConfigPanel.preferredFocusComponent as JTextField).text).isEmpty()

    val treeWalker = TreeWalker(myConfigPanel.component)
    val artSampledButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.SAMPLED_JAVA.getName()
    }
    val artInstrumentedButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.INSTRUMENTED_JAVA.getName()
    }
    val simpleperfButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.SAMPLED_NATIVE.getName()
    }

    assertThat(artSampledButton.isSelected).isFalse()
    assertThat(artInstrumentedButton.isSelected).isFalse()
    assertThat(simpleperfButton.isSelected).isFalse()

    val fileSize = treeWalker.descendants().filterIsInstance<JSlider>().first()
    assertThat(fileSize.value).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)

    val samplingInterval = treeWalker.descendants().filterIsInstance<JSpinner>().first()
    assertThat(samplingInterval.model.value).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)

    val disableLiveAllocation = treeWalker.descendants().filterIsInstance<JCheckBox>().first()
    assertThat(disableLiveAllocation.isSelected).isFalse()
  }

  @Test
  fun fileSizeShouldBeCappedAtMinMaxValues() {
    val treeWalker = TreeWalker(myConfigPanel.component)

    val fileSize = treeWalker.descendants().filterIsInstance<JSlider>().first()
    val fileSizeLimit = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == String.format("%d MB", ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
    }

    fileSize.value = 1234
    assertThat(myConfiguration.profilingBufferSizeInMb).isEqualTo(1234)

    fileSize.value = 5000
    assertThat(myConfiguration.profilingBufferSizeInMb).isEqualTo(myConfigPanel.maxFileSizeLimitMb)
    assertThat(fileSize.value).isEqualTo(myConfigPanel.maxFileSizeLimitMb)
    assertThat(fileSizeLimit.text).isEqualTo(String.format("%.2f GB", myConfigPanel.maxFileSizeLimitMb / 1024.0))

    fileSize.value = 1
    assertThat(myConfiguration.profilingBufferSizeInMb).isEqualTo(CpuProfilingConfigPanel.MIN_FILE_SIZE_LIMIT_MB)
    assertThat(fileSize.value).isEqualTo(CpuProfilingConfigPanel.MIN_FILE_SIZE_LIMIT_MB)
    assertThat(fileSizeLimit.text).isEqualTo(String.format("%d MB", CpuProfilingConfigPanel.MIN_FILE_SIZE_LIMIT_MB))
  }

  @Test
  fun testDisableLiveAllocationPresence() {
    // Requires an O+ device and feature flag enabled.
    assertThat(TreeWalker(myConfigPanel.component).descendants().filterIsInstance<JCheckBox>()).isNotEmpty()

    // Pre-O device
    myConfigPanel = CpuProfilingConfigPanel(AndroidVersion.VersionCodes.N_MR1)
    assertThat(TreeWalker(myConfigPanel.component).descendants().filterIsInstance<JCheckBox>()).isEmpty()

    // Feature flag disabled
    StudioFlags.PROFILER_SAMPLE_LIVE_ALLOCATIONS.override(false)
    myConfigPanel = CpuProfilingConfigPanel(AndroidVersion.VersionCodes.O)
    assertThat(TreeWalker(myConfigPanel.component).descendants().filterIsInstance<JCheckBox>()).isEmpty()
  }
}
