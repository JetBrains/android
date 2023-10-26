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

class TaskSettingConfigsStateTest {
  private val myConfigsState = TaskSettingConfigsState()

  @Test
  fun testDefaultConfigs() {
    val configs = TaskSettingConfigsState.getDefaultConfigs()
    assertThat(configs.map { it.name }).containsExactly(
      TaskSettingConfig.Technology.SAMPLED_NATIVE.getName(),
      TaskSettingConfig.Technology.SYSTEM_TRACE.getName(),
      TaskSettingConfig.Technology.INSTRUMENTED_JAVA.getName(),
      TaskSettingConfig.Technology.SAMPLED_JAVA.getName(),
    ).inOrder()
  }

  @Test
  fun testTaskConfigWhenItsEmpty() {
    val result = myConfigsState.taskConfigs
    // Default task configs added when task config is empty
    assertThat(result.size).isEqualTo(3)
    assertThat(result[0].name).isEqualTo("Callstack Sample")
    assertThat(result[1].name).isEqualTo("Java/Kotlin Method Trace")
    assertThat(result[2].name).isEqualTo("Java/Kotlin Method Sample (legacy)")
  }

  @Test
  fun testTaskConfigWhenNotEmpty() {
    val configsToSave: ArrayList<TaskSettingConfig> = ArrayList()
    configsToSave.add(TaskSettingConfig("HelloTest1", TaskSettingConfig.Technology.INSTRUMENTED_JAVA))
    configsToSave.add(TaskSettingConfig("HelloTest2", TaskSettingConfig.Technology.SAMPLED_NATIVE))
    configsToSave.add(TaskSettingConfig("HelloTest3", TaskSettingConfig.Technology.SAMPLED_JAVA))
    myConfigsState.taskConfigs = configsToSave
    // Verify set task configs
    val result = myConfigsState.taskConfigs;
    assertThat(result.size).isEqualTo(3)
    assertThat(result[0].name).isEqualTo("HelloTest1")
    assertThat(result[1].name).isEqualTo("HelloTest2")
    assertThat(result[2].name).isEqualTo("HelloTest3")
  }

  @Test
  fun testTaskConfigAfterEmptyAssign() {
    val configsToSave: ArrayList<TaskSettingConfig> = ArrayList()
    // Set task configs to be empty
    myConfigsState.taskConfigs = configsToSave
    // Verify task config
    val result = myConfigsState.taskConfigs
    assertThat(result.size).isEqualTo(3)
    assertThat(result[0].name).isEqualTo("Callstack Sample")
    assertThat(result[1].name).isEqualTo("Java/Kotlin Method Trace")
    assertThat(result[2].name).isEqualTo("Java/Kotlin Method Sample (legacy)")
  }

  @Test
  fun testTaskConfigSaveWillSetNewValues() {
    val configsToSave: ArrayList<TaskSettingConfig> = ArrayList()
    configsToSave.add(TaskSettingConfig("HelloTest1", TaskSettingConfig.Technology.INSTRUMENTED_JAVA))
    configsToSave.add(TaskSettingConfig("HelloTest2", TaskSettingConfig.Technology.SAMPLED_NATIVE))
    configsToSave.add(TaskSettingConfig("HelloTest3", TaskSettingConfig.Technology.SAMPLED_JAVA))
    myConfigsState.taskConfigs = configsToSave
    var result = myConfigsState.taskConfigs
    assertThat(result.size).isEqualTo(3)
    assertThat(result[0].name).isEqualTo("HelloTest1")
    assertThat(result[1].name).isEqualTo("HelloTest2")
    assertThat(result[2].name).isEqualTo("HelloTest3")

    val configsToSaveNew: ArrayList<TaskSettingConfig> = ArrayList()
    configsToSaveNew.add(TaskSettingConfig("HelloTest4", TaskSettingConfig.Technology.INSTRUMENTED_JAVA))
    // Task config is reassigned
    myConfigsState.taskConfigs = configsToSaveNew
    result = myConfigsState.taskConfigs
    // Config reflects latest update
    assertThat(result.size).isEqualTo(1)
    assertThat(result[0].name).isEqualTo("HelloTest4")
  }

  @Test
  fun getConfigByNameFromDefaultConfigs() {
    assertThat(myConfigsState.getConfigByName(TaskSettingConfig.Technology.SAMPLED_JAVA.getName())?.technology)
      .isEqualTo(TaskSettingConfig.Technology.SAMPLED_JAVA)
    assertThat(myConfigsState.getConfigByName(TaskSettingConfig.Technology.SAMPLED_NATIVE.getName())?.technology)
      .isEqualTo(TaskSettingConfig.Technology.SAMPLED_NATIVE)
    assertThat(myConfigsState.getConfigByName(TaskSettingConfig.Technology.INSTRUMENTED_JAVA.getName())?.technology)
      .isEqualTo(TaskSettingConfig.Technology.INSTRUMENTED_JAVA)
    assertThat(myConfigsState.getConfigByName(TaskSettingConfig.Technology.SYSTEM_TRACE.getName())?.technology)
      .isEqualTo(TaskSettingConfig.Technology.SYSTEM_TRACE)
  }

  @Test
  fun addUserConfig() {
    val added = myConfigsState.addUserConfig(TaskSettingConfig("MyConfig", TaskSettingConfig.Technology.SAMPLED_JAVA))
    assertThat(added).isTrue()
    assertThat(myConfigsState.userConfigs).hasSize(1)
    assertThat(myConfigsState.userConfigs[0].name).isEqualTo("MyConfig")
  }

  @Test
  fun addUserConfigWithDefaultName() {
    val config = TaskSettingConfig(
      TaskSettingConfig.Technology.SAMPLED_JAVA.getName(),
      TaskSettingConfig.Technology.SAMPLED_JAVA)
    val added = myConfigsState.addUserConfig(config)
    assertThat(added).isFalse()
    assertThat(myConfigsState.userConfigs).hasSize(0)
  }

  @Test
  fun addUserConfigWithDuplicatedName() {
    val configSampled = TaskSettingConfig("MyConfig", TaskSettingConfig.Technology.SAMPLED_JAVA)
    assertThat(myConfigsState.addUserConfig(configSampled)).isTrue()
    val configInstrumented = TaskSettingConfig("MyConfig", TaskSettingConfig.Technology.INSTRUMENTED_JAVA)
    assertThat(myConfigsState.addUserConfig(configInstrumented)).isFalse()
  }

  @Test
  fun getConfigByNameCustomConfig() {
    myConfigsState.userConfigs = listOf(TaskSettingConfig("MyConfig", TaskSettingConfig.Technology.SAMPLED_JAVA))
    assertThat(myConfigsState.getConfigByName("MyConfig")?.name).isEqualTo("MyConfig")
  }

  @Test
  fun getConfigByNameInvalid() {
    assertThat(myConfigsState.getConfigByName("invalid")).isNull()
  }
}