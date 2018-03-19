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
package com.android.tools.datastore.energy

import com.android.tools.profiler.proto.CpuProfiler.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuConfigTest {
  @Test
  fun invalidConfigWorks() {
    val message = CpuCoreConfigResponse.newBuilder()
      .addConfigs(
          CpuCoreConfigResponse.CpuCoreConfigData.newBuilder()
            .setCore(0).setMinFrequencyInKhz(100).setMaxFrequencyInKhz(100).build()
      )
      .addConfigs(
          CpuCoreConfigResponse.CpuCoreConfigData.newBuilder()
            .setCore(1).setMinFrequencyInKhz(200).setMaxFrequencyInKhz(200).build()
      )
      .build()

    val config = CpuConfig(message)
    assertThat(config.isMinMaxCoreFreqValid).isFalse()

    val usages = config.getCpuCoreUsages(
        CpuUsageData.newBuilder()
          .addCores(CpuCoreUsageData.newBuilder().setCore(0).build())
          .addCores(CpuCoreUsageData.newBuilder().setCore(1).build())
          .build(),
        CpuUsageData.newBuilder()
          .setAppCpuTimeInMillisec(50)
          .setElapsedTimeInMillisec(100)
          .setSystemCpuTimeInMillisec(50)
          .addCores(CpuCoreUsageData.newBuilder().setCore(0).build())
          .addCores(CpuCoreUsageData.newBuilder().setCore(1).build())
          .build()
    )

    assertThat(usages[0].myAppUsage).isEqualTo(1.0)
    assertThat(usages[1].myAppUsage).isEqualTo(0.0)
  }

  @Test
  fun emptyCoreConfigWorks() {
    val message = CpuCoreConfigResponse.newBuilder().build()
    val config = CpuConfig(message)
    assertThat(config.isMinMaxCoreFreqValid).isFalse()
  }

  @Test
  fun validConfigWorks() {
    val message = CpuCoreConfigResponse.newBuilder()
      .addConfigs(
          CpuCoreConfigResponse.CpuCoreConfigData.newBuilder()
            .setCore(0).setMinFrequencyInKhz(200).setMaxFrequencyInKhz(400).build()
      )
      .addConfigs(
          CpuCoreConfigResponse.CpuCoreConfigData.newBuilder()
            .setCore(1).setMinFrequencyInKhz(100).setMaxFrequencyInKhz(200).build()
      )
      .build()

    val config1 = CpuConfig(message)
    assertThat(config1.isMinMaxCoreFreqValid).isTrue()
    val usages1 = config1.getCpuCoreUsages(
        CpuUsageData.newBuilder()
          .addCores(CpuCoreUsageData.newBuilder().setCore(0).build())
          .addCores(CpuCoreUsageData.newBuilder().setCore(1).build())
          .build(),
        CpuUsageData.newBuilder()
          .setAppCpuTimeInMillisec(25)
          .setElapsedTimeInMillisec(100)
          .setSystemCpuTimeInMillisec(50)
          .addCores(
              CpuCoreUsageData.newBuilder()
                .setCore(0).setSystemCpuTimeInMillisec(25).setElapsedTimeInMillisec(100).build())
          .addCores(
              CpuCoreUsageData.newBuilder()
                .setCore(1).setSystemCpuTimeInMillisec(75).setElapsedTimeInMillisec(100).build())
          .build()
    )

    assertThat(usages1[0].myAppUsage).isEqualTo(0.5)
    assertThat(usages1[0].myCoreUsage).isEqualTo(0.25)
    assertThat(usages1[1].myAppUsage).isEqualTo(0.5)
    assertThat(usages1[1].myCoreUsage).isEqualTo(0.75)
  }
}