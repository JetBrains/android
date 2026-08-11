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
package com.google.idea.bazel.java.run.producers

import com.google.idea.bazel.java.run.BlazeJavaRunConfigurationHandler
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.execution.BlazeParametersListUtil
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationHandler
import com.google.idea.blaze.base.run.producers.BlazeCommandRunConfigurationHelper
import com.google.idea.blaze.base.run.producers.CommandComponent
import com.google.idea.blaze.base.run.producers.TestFilterComponent
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState

/** Encodes and decodes JVM test filters for Java/Kotlin run configurations. */
class JvmBlazeCommandRunConfigurationHelper : BlazeCommandRunConfigurationHelper {

  override fun handlesConfiguration(config: BlazeCommandRunConfiguration): Boolean {
    return config.handler is BlazeJavaRunConfigurationHandler || config.handler is BlazeCommandGenericRunConfigurationHandler
  }

  override fun applyCommand(config: BlazeCommandRunConfiguration, command: CommandComponent) {
    val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return
    commonState.commandState.command = command.command
    val flags = ArrayList(commonState.blazeFlagsState.rawFlags)
    for (flag in command.extraFlags) {
      if (!flags.contains(flag)) {
        flags.add(flag)
      }
    }
    commonState.blazeFlagsState.rawFlags = flags
  }

  override fun matchesCommand(config: BlazeCommandRunConfiguration, command: CommandComponent): Boolean {
    val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
    return commonState.commandState.command == command.command
  }

  override fun applyFilter(config: BlazeCommandRunConfiguration, testFilter: TestFilterComponent) {
    val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return
    val filterString = BlazeJUnitTestFilterFlags.testFilterForSelectors(testFilter.testSelectors)
    val flags = ArrayList(commonState.blazeFlagsState.rawFlags)
    flags.removeIf { it.startsWith(BlazeFlags.TEST_FILTER) }
    if (!filterString.isNullOrEmpty()) {
      flags.add("${BlazeFlags.TEST_FILTER}=${BlazeParametersListUtil.encodeParam(filterString)}")
    }
    commonState.blazeFlagsState.rawFlags = flags
  }

  override fun matchesFilter(config: BlazeCommandRunConfiguration, testFilter: TestFilterComponent): Boolean {
    val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
    val expectedFilter = BlazeJUnitTestFilterFlags.testFilterForSelectors(testFilter.testSelectors) ?: return false
    val expectedFlag = "${BlazeFlags.TEST_FILTER}=${BlazeParametersListUtil.encodeParam(expectedFilter)}"
    return commonState.blazeFlagsState.rawFlags.contains(expectedFlag) || commonState.testFilterForExternalProcesses == expectedFilter
  }

  override fun hasActiveFilter(config: BlazeCommandRunConfiguration): Boolean {
    val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
    return commonState.testFilterFlag != null
  }
}
