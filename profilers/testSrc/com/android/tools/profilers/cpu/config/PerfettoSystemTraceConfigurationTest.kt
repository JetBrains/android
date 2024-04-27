package com.android.tools.profilers.cpu.config

import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test


class PerfettoSystemTraceConfigurationTest {

  @Test
  fun getRequiredDeviceLevelTraceBoxEnabled() {
     val perfettoSystemTraceConfiguration = PerfettoSystemTraceConfiguration("SampleFromUnitTest", true);
     val result = perfettoSystemTraceConfiguration.requiredDeviceLevel;
     assertThat(result).isEqualTo(AndroidVersion.VersionCodes.M);
  }

  @Test
  fun getRequiredDeviceLevelTraceBoxDisabled() {
    val perfettoSystemTraceConfiguration = PerfettoSystemTraceConfiguration("SampleFromUnitTest", false);
    val result = perfettoSystemTraceConfiguration.requiredDeviceLevel;
    assertThat(result).isEqualTo(AndroidVersion.VersionCodes.P);
  }
}