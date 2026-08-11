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
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils
import com.intellij.execution.actions.ConfigurationContext

/**
 * Handles the specific case where the user creates a run configuration by selecting test suites / classes / methods from the test UI tree.
 *
 * In this special case we already know the blaze target string, and apply domain test targets or filters to the existing configuration.
 */
class BlazeFilterExistingRunConfigurationProducer :
  BlazeRunConfigurationProducer<UnifiedRunContext>(BlazeCommandRunConfigurationType.getInstance()) {

  override fun findContext(context: ConfigurationContext): UnifiedRunContext? {
    val psi = context.psiLocation ?: return null
    val base = context.getOriginalConfiguration(null) as? BlazeCommandRunConfiguration ?: return null
    val patterns = base.targetPatterns
    if (patterns.isEmpty()) return null

    val selectedElements = SmRunnerUtils.getSelectedSmRunnerTreeElements(context)
    if (selectedElements.isEmpty()) return null

    val testEventsHandler = BlazeTestEventsHandler.getHandlerForTargets(context.project, patterns).orElse(null) ?: return null
    val testSelectors = testEventsHandler.getTestSelectors(context.project, selectedElements) ?: return null

    return UnifiedRunContext(
      sourceElement = psi,
      target = TargetSpecification.ExplicitPatterns(patterns),
      testFilter = TestFilterComponent(testSelectors, true),
      command = CommandComponent(BlazeCommandName.TEST, emptyList()),
    )
  }
}
