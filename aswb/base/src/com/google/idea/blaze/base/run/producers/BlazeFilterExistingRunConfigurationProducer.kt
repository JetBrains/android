/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers

import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.PsiElement

/**
 * Handles the specific case where the user creates a run configuration by selecting test suites / classes / methods from the test UI tree.
 *
 * In this special case we already know the blaze target string, and only need to apply a filter to the existing configuration. Delegates
 * language-specific filter calculation to [BlazeTestEventsHandler].
 */
class BlazeFilterExistingRunConfigurationProducer :
  BlazeRunConfigurationProducer<BlazeFilterExistingRunConfigurationProducer.FilterContext>(BlazeCommandRunConfigurationType.getInstance()) {

  class FilterContext(override val sourceElement: PsiElement, val testFilter: String, val countSelectedTestCases: Int) :
    RunConfigurationContext {

    override fun setupRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
      val handlerState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
      if (BlazeCommandName.TEST != handlerState.commandState.command) {
        return false
      }
      val flags = ArrayList(handlerState.blazeFlagsState.rawFlags)
      flags.removeIf { flag -> flag.startsWith(BlazeFlags.TEST_FILTER) }
      flags.add(testFilter)

      if (countSelectedTestCases == 1 && !flags.contains(BlazeFlags.DISABLE_TEST_SHARDING)) {
        flags.add(BlazeFlags.DISABLE_TEST_SHARDING)
      }
      handlerState.blazeFlagsState.rawFlags = flags
      config.name = config.name + " (filtered)"
      config.setNameChangedByUser(true)
      return true
    }

    override fun matchesRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
      val handlerState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
      return handlerState.commandState.command == BlazeCommandName.TEST && testFilter == handlerState.testFilterFlag
    }
  }

  override fun findContext(context: ConfigurationContext): FilterContext? {
    val psi = context.psiLocation ?: return null
    val testFilter = getTestFilter(context) ?: return null
    return FilterContext(psi, testFilter, SmRunnerUtils.countSelectedTestCases(context))
  }

  companion object {
    private fun getTestFilter(context: ConfigurationContext): String? {
      val base = context.getOriginalConfiguration(null)
      if (base !is BlazeCommandRunConfiguration) {
        return null
      }
      val patterns = base.targetPatterns
      if (patterns.isEmpty()) {
        return null
      }
      val selectedElements = SmRunnerUtils.getSelectedSmRunnerTreeElements(context)
      if (selectedElements.isEmpty()) {
        return null
      }
      val testEventsHandler = BlazeTestEventsHandler.getHandlerForTargets(context.project, patterns)
      return testEventsHandler.map { handler -> handler.getTestFilter(context.project, selectedElements) }.orElse(null)
    }
  }
}
