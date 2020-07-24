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
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.ProfilingTechnology
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration
import com.android.tools.profilers.cpu.config.AtraceConfiguration
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JRadioButton
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTextField


class CpuProfilingConfigPanelTest {
  @get:Rule
  val myRestoreFlagRule = RestoreFlagRule(StudioFlags.PROFILER_SAMPLE_LIVE_ALLOCATIONS)

  private lateinit var myConfigPanel: CpuProfilingConfigPanel
  private lateinit var myConfiguration: ArtSampledConfiguration

  fun setUp(deviceApiLevel: Int) {
    myConfigPanel = CpuProfilingConfigPanel(deviceApiLevel)
    myConfiguration = ArtSampledConfiguration("myConfig")
    myConfigPanel.setConfiguration(myConfiguration, false)
  }

  @Before
  fun setUp() {
    setUp(AndroidVersion.VersionCodes.O)
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
  fun fieldsAreDisabledWithAtraceSet() {
    myConfigPanel.setConfiguration(
      AtraceConfiguration("Test"), false)

    val treeWalker = TreeWalker(myConfigPanel.component)
    // All elements are enabled in non-default config.
    radioButtonsValidation(treeWalker, true, true, true, true)
    // Atrace has file size disabled by default.
    fileSizeButtonValidation(treeWalker, false)
    sampleSizeValidation(treeWalker, false)
    liveAllocationValidation(treeWalker, true)
  }

  @Test
  fun fieldsAreEnabledWithArtSampled() {
    myConfigPanel.setConfiguration(ArtSampledConfiguration("Test"), false)

    val treeWalker = TreeWalker(myConfigPanel.component)
    // All elements are enabled in non-default config.
    radioButtonsValidation(treeWalker, true, true, true, true)
    // Atrace has file size disabled by default.
    fileSizeButtonValidation(treeWalker, false)
    sampleSizeValidation(treeWalker, true)
    liveAllocationValidation(treeWalker, true)
  }

  @Test
  fun fieldsAreEnabledWithArtInstrumented() {
    myConfigPanel.setConfiguration(ArtInstrumentedConfiguration("Test"), false)

    val treeWalker = TreeWalker(myConfigPanel.component)
    // All elements are enabled in non-default config.
    radioButtonsValidation(treeWalker, true, true, true, true)
    // Atrace has file size disabled by default.
    fileSizeButtonValidation(treeWalker, false)
    sampleSizeValidation(treeWalker, false)
    liveAllocationValidation(treeWalker, true)
  }

  @Test
  fun fieldsAreEnabledWithSimplePerf() {
    myConfigPanel.setConfiguration(SimpleperfConfiguration("Test"), false)

    val treeWalker = TreeWalker(myConfigPanel.component)
    // All elements are enabled in non-default config.
    radioButtonsValidation(treeWalker, true, true, true, true)
    // Atrace has file size disabled by default.
    fileSizeButtonValidation(treeWalker, false)
    sampleSizeValidation(treeWalker, true)
    liveAllocationValidation(treeWalker, true)
  }

  @Test
  fun sizeBufferEnabledOnlyForArtInPreO() {
    setUp(AndroidVersion.VersionCodes.N)

    myConfigPanel.setConfiguration(ArtSampledConfiguration("Test"),
                                   false)
    val treeWalker = TreeWalker(myConfigPanel.component)
    fileSizeButtonValidation(treeWalker, true)

    myConfigPanel.setConfiguration(ArtInstrumentedConfiguration("Test"), false)
    fileSizeButtonValidation(treeWalker, true)
    myConfigPanel.setConfiguration(SimpleperfConfiguration("Test"), false)
    fileSizeButtonValidation(treeWalker, false)
    myConfigPanel.setConfiguration(AtraceConfiguration("Test"), false)
    fileSizeButtonValidation(treeWalker, false)
  }

  @Test
  fun testUsingDefaultConfiguration() {
    val defaultConfig = ArtSampledConfiguration("myConfig")
    myConfigPanel.setConfiguration(defaultConfig, true)

    val treeWalker = TreeWalker(myConfigPanel.component)
    assertThat(myConfigPanel.preferredFocusComponent.isEnabled).isFalse()
    radioButtonsValidation(treeWalker, false, false, false, false)
    fileSizeButtonValidation(treeWalker, false)
    sampleSizeValidation(treeWalker, false)
    liveAllocationValidation(treeWalker, false)
  }

  fun liveAllocationValidation(treeWalker: TreeWalker, enabled: Boolean) {
    val disableLiveAllocation = treeWalker.descendants().filterIsInstance<JCheckBox>().first()
    val disableLiveAllocationDescription = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == CpuProfilingConfigPanel.DISABLE_LIVE_ALLOCATION_DESCRIPTION
    }

    assertThat(disableLiveAllocation.isEnabled).isSameAs(enabled)
    assertThat(disableLiveAllocationDescription.isEnabled).isSameAs(enabled)
  }

  fun sampleSizeValidation(treeWalker: TreeWalker, enabled: Boolean) {
    val samplingInterval = treeWalker.descendants().filterIsInstance<JSpinner>().first()
    val samplingIntervalText = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == CpuProfilingConfigPanel.SAMPLING_INTERVAL
    }
    val samplingIntervalUnit = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == CpuProfilingConfigPanel.SAMPLING_INTERVAL_UNIT
    }

    assertThat(samplingInterval.isEnabled).isSameAs(enabled)
    assertThat(samplingIntervalText.isEnabled).isSameAs(enabled)
    assertThat(samplingIntervalUnit.isEnabled).isSameAs(enabled)
  }

  fun fileSizeButtonValidation(treeWalker: TreeWalker, enabled: Boolean) {
    val fileSize = treeWalker.descendants().filterIsInstance<JSlider>().first()
    val fileSizeLimit = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == String.format("%d MB", ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
    }
    val fileSizeLimitText = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == CpuProfilingConfigPanel.FILE_SIZE_LIMIT
    }
    val fileSizeLimitDescription = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == CpuProfilingConfigPanel.FILE_SIZE_LIMIT_DESCRIPTION
    }

    assertThat(fileSize.isEnabled).isSameAs(enabled)
    assertThat(fileSizeLimit.isEnabled).isSameAs(enabled)
    assertThat(fileSizeLimitText.isEnabled).isSameAs(enabled)
    assertThat(fileSizeLimitDescription.isEnabled).isSameAs(enabled)
  }

  fun radioButtonsValidation(treeWalker: TreeWalker,
                             artEnabled: Boolean,
                             simplePerfEnabled: Boolean,
                             artInstEnabled: Boolean,
                             systemTraceEnabled: Boolean) {
    val artSampledButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.SAMPLED_JAVA.getName()
    }
    val artInstrumentedButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.INSTRUMENTED_JAVA.getName()
    }
    val simpleperfButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.SAMPLED_NATIVE.getName()
    }
    val systemTraceButton = treeWalker.descendants().filterIsInstance<JRadioButton>().first {
      it.text == CpuProfilerConfig.Technology.SYSTEM_TRACE.getName()
    }
    val artSampledDescription = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == ProfilingTechnology.ART_SAMPLED.longDescription
    }
    val artInstrumentedDescription = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == ProfilingTechnology.ART_INSTRUMENTED.longDescription
    }
    val simpleperfDescription = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == ProfilingTechnology.SIMPLEPERF.longDescription
    }
    val systemTraceDescription = treeWalker.descendants().filterIsInstance<JLabel>().first {
      it.text == ProfilingTechnology.SYSTEM_TRACE.longDescription
    }
    assertThat(artSampledButton.isEnabled).isSameAs(artEnabled)
    assertThat(artInstrumentedButton.isEnabled).isSameAs(artInstEnabled)
    assertThat(simpleperfButton.isEnabled).isSameAs(simplePerfEnabled)
    assertThat(systemTraceButton.isEnabled).isSameAs(systemTraceEnabled)
    assertThat(artSampledDescription.isEnabled).isSameAs(artEnabled)
    assertThat(artInstrumentedDescription.isEnabled).isSameAs(artInstEnabled)
    assertThat(simpleperfDescription.isEnabled).isSameAs(simplePerfEnabled)
    assertThat(systemTraceDescription.isEnabled).isSameAs(systemTraceEnabled)
  }

  @Test
  fun testLoadingConfiguration() {
    val configuration = ArtSampledConfiguration("myConfig").apply {
      profilingBufferSizeInMb = 1234
      profilingSamplingIntervalUs = 56789
      isDisableLiveAllocation = true
    }

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
