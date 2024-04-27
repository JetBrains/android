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
package com.android.tools.idea.run.profiler

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuProfilerConfigsStateTest {
  private val myConfigsState = CpuProfilerConfigsState()

  @Test
  fun testDefaultConfigs() {
    val configs = CpuProfilerConfigsState.getDefaultConfigs()
    assertThat(configs.map { it.name }).containsExactly(
        CpuProfilerConfig.Technology.SAMPLED_NATIVE.getName(),
        CpuProfilerConfig.Technology.SYSTEM_TRACE.getName(),
        CpuProfilerConfig.Technology.INSTRUMENTED_JAVA.getName(),
        CpuProfilerConfig.Technology.SAMPLED_JAVA.getName(),
    ).inOrder()
  }

  @Test
  fun testTaskConfigWhenItsEmpty() {
    val result = myConfigsState.savedTaskConfigsIfPresentOrDefault
    // Default task configs added when task config is empty
    assertThat(result.size).isEqualTo(5)
    assertThat(result[0].name).isEqualTo("Callstack Sample")
    assertThat(result[1].name).isEqualTo("Java/Kotlin Method Trace")
    assertThat(result[2].name).isEqualTo("Java/Kotlin Method Sample (legacy)")
    assertThat(result[3].name).isEqualTo("Native Allocations")
    assertThat(result[4].name).isEqualTo("System Trace")
  }

  @Test
  fun testTaskConfigWhenNotEmpty() {
    val configsToSave: ArrayList<CpuProfilerConfig> = ArrayList()
    configsToSave.add(CpuProfilerConfig("HelloTest1", CpuProfilerConfig.Technology.INSTRUMENTED_JAVA))
    configsToSave.add(CpuProfilerConfig("HelloTest2", CpuProfilerConfig.Technology.SAMPLED_NATIVE))
    configsToSave.add(CpuProfilerConfig("HelloTest3", CpuProfilerConfig.Technology.SAMPLED_JAVA))
    myConfigsState.taskConfigs = configsToSave
    // Verify set task configs
    val result = myConfigsState.savedTaskConfigsIfPresentOrDefault;
    assertThat(result.size).isEqualTo(3)
    assertThat(result[0].name).isEqualTo("HelloTest1")
    assertThat(result[1].name).isEqualTo("HelloTest2")
    assertThat(result[2].name).isEqualTo("HelloTest3")
  }

  @Test
  fun testTaskConfigAfterEmptyAssign() {
    val configsToSave: ArrayList<CpuProfilerConfig> = ArrayList()
    // Set task configs to be empty
    myConfigsState.taskConfigs = configsToSave
    // Verify task config
    val result = myConfigsState.savedTaskConfigsIfPresentOrDefault
    assertThat(result.size).isEqualTo(5)
    assertThat(result[0].name).isEqualTo("Callstack Sample")
    assertThat(result[1].name).isEqualTo("Java/Kotlin Method Trace")
    assertThat(result[2].name).isEqualTo("Java/Kotlin Method Sample (legacy)")
    assertThat(result[3].name).isEqualTo("Native Allocations")
    assertThat(result[4].name).isEqualTo("System Trace")
  }

  @Test
  fun testTaskConfigSaveWillSetNewValues() {
    val configsToSave: ArrayList<CpuProfilerConfig> = ArrayList()
    configsToSave.add(CpuProfilerConfig("HelloTest1", CpuProfilerConfig.Technology.INSTRUMENTED_JAVA))
    configsToSave.add(CpuProfilerConfig("HelloTest2", CpuProfilerConfig.Technology.SAMPLED_NATIVE))
    configsToSave.add(CpuProfilerConfig("HelloTest3", CpuProfilerConfig.Technology.SAMPLED_JAVA))
    configsToSave.add(CpuProfilerConfig("HelloTest4", CpuProfilerConfig.Technology.NATIVE_ALLOCATIONS))
    configsToSave.add(CpuProfilerConfig("HelloTest5", CpuProfilerConfig.Technology.SYSTEM_TRACE))
    myConfigsState.taskConfigs = configsToSave
    var result = myConfigsState.savedTaskConfigsIfPresentOrDefault
    assertThat(result.size).isEqualTo(5)
    assertThat(result[0].name).isEqualTo("HelloTest1")
    assertThat(result[1].name).isEqualTo("HelloTest2")
    assertThat(result[2].name).isEqualTo("HelloTest3")
    assertThat(result[3].name).isEqualTo("HelloTest4")
    assertThat(result[4].name).isEqualTo("HelloTest5")

    val configsToSaveNew: ArrayList<CpuProfilerConfig> = ArrayList()
    configsToSaveNew.add(CpuProfilerConfig("HelloTest10", CpuProfilerConfig.Technology.INSTRUMENTED_JAVA))
    // Task config is reassigned
    myConfigsState.taskConfigs = configsToSaveNew
    result = myConfigsState.savedTaskConfigsIfPresentOrDefault
    // Config reflects latest update
    assertThat(result.size).isEqualTo(1)
    assertThat(result[0].name).isEqualTo("HelloTest10")
  }

  @Test
  fun testRetrieveTaskConfigNativeAllocationUpdatedValue() {
    val configsToSave: ArrayList<CpuProfilerConfig> = ArrayList()
    configsToSave.add(CpuProfilerConfig("HelloTest1", CpuProfilerConfig.Technology.INSTRUMENTED_JAVA))
    configsToSave.add(CpuProfilerConfig("HelloTest2", CpuProfilerConfig.Technology.SAMPLED_NATIVE))
    configsToSave.add(CpuProfilerConfig(CpuProfilerConfig.Technology.NATIVE_ALLOCATIONS))
    configsToSave.add(CpuProfilerConfig("HelloTest3", CpuProfilerConfig.Technology.SAMPLED_JAVA))
    configsToSave.add(CpuProfilerConfig("HelloTest4", CpuProfilerConfig.Technology.SYSTEM_TRACE))
    myConfigsState.taskConfigs = configsToSave
    myConfigsState.taskConfigs[2].samplingRateBytes = 99912
    var result = myConfigsState.savedTaskConfigsIfPresentOrDefault
    assertThat(result.size).isEqualTo(5)
    assertThat(result[0].name).isEqualTo("HelloTest1")
    assertThat(result[1].name).isEqualTo("HelloTest2")
    assertThat(result[2].name).isEqualTo("Native Allocations")
    assertThat(result[3].name).isEqualTo("HelloTest3")
    assertThat(result[4].name).isEqualTo("HelloTest4")

    assertThat(myConfigsState.nativeAllocationsConfigForTaskConfig.samplingRateBytes).isEqualTo(99912)
  }

  @Test
  fun testRetrieveTaskConfigNativeAllocationDefaultValue() {
    val configsToSave: ArrayList<CpuProfilerConfig> = ArrayList()
    configsToSave.add(CpuProfilerConfig("HelloTest1", CpuProfilerConfig.Technology.INSTRUMENTED_JAVA))
    configsToSave.add(CpuProfilerConfig("HelloTest2", CpuProfilerConfig.Technology.SAMPLED_NATIVE))
    configsToSave.add(CpuProfilerConfig("HelloTest3", CpuProfilerConfig.Technology.NATIVE_ALLOCATIONS))
    myConfigsState.taskConfigs = configsToSave
    var result = myConfigsState.savedTaskConfigsIfPresentOrDefault
    assertThat(result.size).isEqualTo(3)
    assertThat(result[0].name).isEqualTo("HelloTest1")
    assertThat(result[1].name).isEqualTo("HelloTest2")
    assertThat(result[2].name).isEqualTo("HelloTest3")

    // 2048 is the default samplingRateBytes value for native memory
    assertThat(myConfigsState.nativeAllocationsConfigForTaskConfig.samplingRateBytes).isEqualTo(2048)
  }

  @Test
  fun getConfigByNameFromDefaultConfigs() {
    assertThat(myConfigsState.getConfigByName(CpuProfilerConfig.Technology.SAMPLED_JAVA.getName())?.technology)
      .isEqualTo(CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(myConfigsState.getConfigByName(CpuProfilerConfig.Technology.SAMPLED_NATIVE.getName())?.technology)
      .isEqualTo(CpuProfilerConfig.Technology.SAMPLED_NATIVE)
    assertThat(myConfigsState.getConfigByName(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA.getName())?.technology)
      .isEqualTo(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA)
    assertThat(myConfigsState.getConfigByName(CpuProfilerConfig.Technology.SYSTEM_TRACE.getName())?.technology)
      .isEqualTo(CpuProfilerConfig.Technology.SYSTEM_TRACE)
  }

  @Test
  fun addUserConfig() {
    val added = myConfigsState.addUserConfig(CpuProfilerConfig("MyConfig", CpuProfilerConfig.Technology.SAMPLED_JAVA))
    assertThat(added).isTrue()
    assertThat(myConfigsState.userConfigs).hasSize(1)
    assertThat(myConfigsState.userConfigs[0].name).isEqualTo("MyConfig")
  }

  @Test
  fun addUserConfigWithDefaultName() {
    val config = CpuProfilerConfig(CpuProfilerConfig.Technology.SAMPLED_JAVA.getName(), CpuProfilerConfig.Technology.SAMPLED_JAVA)
    val added = myConfigsState.addUserConfig(config)
    assertThat(added).isFalse()
    assertThat(myConfigsState.userConfigs).hasSize(0)
  }

  @Test
  fun addUserConfigWithDuplicatedName() {
    val configSampled = CpuProfilerConfig("MyConfig", CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(myConfigsState.addUserConfig(configSampled)).isTrue()
    val configInstrumented = CpuProfilerConfig("MyConfig", CpuProfilerConfig.Technology.INSTRUMENTED_JAVA)
    assertThat(myConfigsState.addUserConfig(configInstrumented)).isFalse()
  }

  @Test
  fun getConfigByNameCustomConfig() {
    myConfigsState.userConfigs = listOf(CpuProfilerConfig("MyConfig", CpuProfilerConfig.Technology.SAMPLED_JAVA))
    assertThat(myConfigsState.getConfigByName("MyConfig")?.name).isEqualTo("MyConfig")
  }

  @Test
  fun getConfigByNameInvalid() {
    assertThat(myConfigsState.getConfigByName("invalid")).isNull()
  }
}