/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependencies

data class PluginInsertionConfig(
  val trySteps: LinkedHashSet<PluginInsertionStep>,
  val whenFoundSame: MatchedStrategy,
  val variableName: String? = null,
  val addRepoForSnapshots: Boolean? = null
) {

  // specifies what to do when found plugin with same id but different version
  enum class MatchedStrategy {
    UPDATE_VERSION, DO_NOTHING
  }

  // specifies where to try insert plugin
  enum class PluginInsertionStep {
    BUILDSCRIPT_CLASSPATH, BUILDSCRIPT_CLASSPATH_WITH_VARIABLE, PLUGIN_MANAGEMENT, PLUGIN_BLOCK
  }

  companion object {
    fun defaultInsertionConfig(): PluginInsertionConfig {
      val steps = LinkedHashSet<PluginInsertionStep>()
      steps.addAll(listOf(PluginInsertionStep.PLUGIN_MANAGEMENT,
                          PluginInsertionStep.PLUGIN_BLOCK,
                          PluginInsertionStep.BUILDSCRIPT_CLASSPATH))
      return PluginInsertionConfig(
        steps,
        MatchedStrategy.DO_NOTHING
      )
    }
  }
}


