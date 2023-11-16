package com.android.tools.profilers.cpu.config

import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test


class PerfettoConfigurationTest {

  @Test
  fun getRequiredDeviceLevelTraceBoxEnabled() {
     val perfettoConfiguration = PerfettoConfiguration("SampleFromUnitTest", true);
     val result = perfettoConfiguration.requiredDeviceLevel;
     assertThat(result).isEqualTo(AndroidVersion.VersionCodes.M);
  }

  @Test
  fun getRequiredDeviceLevelTraceBoxDisabled() {
    val perfettoConfiguration = PerfettoConfiguration("SampleFromUnitTest", false);
    val result = perfettoConfiguration.requiredDeviceLevel;
    assertThat(result).isEqualTo(AndroidVersion.VersionCodes.P);
  }
}