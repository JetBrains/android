/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.producers

import com.google.idea.blaze.android.run.test.BlazeAndroidTestRunConfigurationHandler
import com.google.idea.blaze.android.run.test.BlazeAndroidTestRunConfigurationState
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.producers.BlazeCommandRunConfigurationHelper
import com.google.idea.blaze.base.run.producers.CommandComponent
import com.google.idea.blaze.base.run.producers.TestFilterComponent

/** Encodes and decodes Android test filters for Android run configurations. */
class AndroidBlazeCommandRunConfigurationHelper : BlazeCommandRunConfigurationHelper {

  override fun handlesConfiguration(config: BlazeCommandRunConfiguration): Boolean {
    return config.handler is BlazeAndroidTestRunConfigurationHandler
  }

  override fun applyCommand(config: BlazeCommandRunConfiguration, command: CommandComponent) {
    val state = config.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState::class.java) ?: return
    val flags = ArrayList(state.commonState.blazeFlagsState.rawFlags)
    for (flag in command.extraFlags) {
      if (!flags.contains(flag)) {
        flags.add(flag)
      }
    }
    state.commonState.blazeFlagsState.rawFlags = flags
  }

  override fun matchesCommand(config: BlazeCommandRunConfiguration, command: CommandComponent): Boolean {
    return command.command == BlazeCommandName.TEST
  }

  override fun applyFilter(config: BlazeCommandRunConfiguration, testFilter: TestFilterComponent) {
    val state = config.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState::class.java) ?: return
    val testSelectors = testFilter.testSelectors
    if (testSelectors.isEmpty()) {
      state.testingType = BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_MODULE
      state.className = ""
      state.methodName = ""
      return
    }

    val singleSelector = testSelectors.firstOrNull()
    if (testSelectors.size == 1 && singleSelector != null) {
      if (singleSelector.methodName != null) {
        state.testingType = BlazeAndroidTestRunConfigurationState.TEST_METHOD
        state.className = singleSelector.className
        state.methodName = singleSelector.methodName
      } else {
        state.testingType = BlazeAndroidTestRunConfigurationState.TEST_CLASS
        state.className = singleSelector.className
        state.methodName = ""
      }
    } else {
      // NOTE: BlazeAndroidTestRunConfigurationState only supports filtering a single class or method.
      // Multiple target selections cannot be represented, so we fall back to TEST_ALL_IN_MODULE.
      state.testingType = BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_MODULE
      state.className = ""
      state.methodName = ""
    }
  }

  override fun matchesFilter(config: BlazeCommandRunConfiguration, testFilter: TestFilterComponent): Boolean {
    val state = config.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState::class.java) ?: return false
    val selector = testFilter.testSelectors.singleOrNull()
    return when {
      selector?.methodName != null ->
        state.testingType == BlazeAndroidTestRunConfigurationState.TEST_METHOD &&
          state.className == selector.className &&
          state.methodName == selector.methodName
      selector != null -> state.testingType == BlazeAndroidTestRunConfigurationState.TEST_CLASS && state.className == selector.className
      else -> state.testingType == BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_MODULE && state.className.isEmpty()
    }
  }

  override fun hasActiveFilter(config: BlazeCommandRunConfiguration): Boolean {
    val state = config.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState::class.java) ?: return false
    return state.testingType != BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_MODULE || state.className.isNotEmpty()
  }
}
