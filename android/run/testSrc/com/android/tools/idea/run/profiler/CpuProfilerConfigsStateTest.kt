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
    assertThat(result.size).isEqualTo(3)
    assertThat(result[0].name).isEqualTo("Callstack Sample")
    assertThat(result[1].name).isEqualTo("Java/Kotlin Method Trace")
    assertThat(result[2].name).isEqualTo("Java/Kotlin Method Sample (legacy)")
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
    assertThat(result.size).isEqualTo(3)
    assertThat(result[0].name).isEqualTo("Callstack Sample")
    assertThat(result[1].name).isEqualTo("Java/Kotlin Method Trace")
    assertThat(result[2].name).isEqualTo("Java/Kotlin Method Sample (legacy)")
  }

  @Test
  fun testTaskConfigSaveWillSetNewValues() {
    val configsToSave: ArrayList<CpuProfilerConfig> = ArrayList()
    configsToSave.add(CpuProfilerConfig("HelloTest1", CpuProfilerConfig.Technology.INSTRUMENTED_JAVA))
    configsToSave.add(CpuProfilerConfig("HelloTest2", CpuProfilerConfig.Technology.SAMPLED_NATIVE))
    configsToSave.add(CpuProfilerConfig("HelloTest3", CpuProfilerConfig.Technology.SAMPLED_JAVA))
    myConfigsState.taskConfigs = configsToSave
    var result = myConfigsState.savedTaskConfigsIfPresentOrDefault
    assertThat(result.size).isEqualTo(3)
    assertThat(result[0].name).isEqualTo("HelloTest1")
    assertThat(result[1].name).isEqualTo("HelloTest2")
    assertThat(result[2].name).isEqualTo("HelloTest3")

    val configsToSaveNew: ArrayList<CpuProfilerConfig> = ArrayList()
    configsToSaveNew.add(CpuProfilerConfig("HelloTest4", CpuProfilerConfig.Technology.INSTRUMENTED_JAVA))
    // Task config is reassigned
    myConfigsState.taskConfigs = configsToSaveNew
    result = myConfigsState.savedTaskConfigsIfPresentOrDefault
    // Config reflects latest update
    assertThat(result.size).isEqualTo(1)
    assertThat(result[0].name).isEqualTo("HelloTest4")
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