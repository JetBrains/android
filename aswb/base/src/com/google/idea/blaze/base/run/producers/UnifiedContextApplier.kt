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
package com.google.idea.blaze.base.run.producers

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState

/** Orchestrates Stage 3 application and matching of UnifiedRunContext to BlazeCommandRunConfiguration. */
object UnifiedContextApplier {
  fun apply(config: BlazeCommandRunConfiguration, context: UnifiedRunContext): Boolean {
    var applied = false

    // 1. Target setting MUST run first to instantiate config.handler via updateHandler()
    context.target?.let {
      if (!TargetApplier.apply(config, it)) {
        return false
      }
      applied = true
    }

    // 2. Command setting operates AFTER config.handler is instantiated
    context.command?.let {
      CommandApplier.apply(config, it)
      applied = true
    }

    // 3. Handler-Specific Application: TestFilterApplier operates AFTER TargetApplier has instantiated config.handler
    context.testFilter?.let {
      TestFilterApplier.apply(config, it)
      applied = true
    }

    if (context.testFilter?.appendFilteredSuffix == true) {
      config.name = config.name + " (filtered)"
      config.setNameChangedByUser(true)
    } else if (context.testFilter != null) {
      val description = getFilterDescription(context.testFilter.testSelectors)
      if (description != null) {
        val nameBuilder = BlazeConfigurationNameBuilder(config)
        context.command?.command?.let { nameBuilder.setCommandName(it.toString()) }
        nameBuilder.setTargetString(description)
        config.name = nameBuilder.build()
        config.setNameChangedByUser(true)
      } else if (applied) {
        config.setGeneratedName()
      }
    }
    return applied
  }

  fun getFilterDescription(testSelectors: List<TestSelector>): String? {
    if (testSelectors.isEmpty()) return null
    val first = testSelectors.first()
    val className = first.className.substringAfterLast('.')
    val methodNames = testSelectors.mapNotNull { it.methodName }.distinct()
    return if (methodNames.isEmpty()) {
      className
    } else {
      "$className.${methodNames.joinToString(",")}"
    }
  }

  fun matches(config: BlazeCommandRunConfiguration, context: UnifiedRunContext): Boolean {
    context.command?.let { if (!CommandApplier.matches(config, it)) return false }
    context.target?.let { if (!TargetApplier.matches(config, it)) return false }

    if (context.testFilter != null) {
      if (!TestFilterApplier.matches(config, context.testFilter)) return false
    } else {
      if (hasActiveTestFilter(config)) return false
    }
    return true
  }

  fun hasActiveTestFilter(config: BlazeCommandRunConfiguration): Boolean {
    val helper = BlazeCommandRunConfigurationHelper.EP_NAME.extensions.firstOrNull { it.handlesConfiguration(config) }
    if (helper != null && helper.hasActiveFilter(config)) return true
    val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java)
    return commonState?.testFilterFlag != null
  }
}

object TargetApplier {
  /**
   * Applies the target specification to the configuration during Stage 3 setup.
   *
   * Expects a resolved or explicit target specification. Returns false if target resolution is pending.
   */
  fun apply(config: BlazeCommandRunConfiguration, spec: TargetSpecification): Boolean {
    return when (spec) {
      is TargetSpecification.Resolved -> {
        config.setTargetInfo(spec.targetInfo)
        true
      }
      is TargetSpecification.ExplicitPatterns -> {
        config.setTargetPatterns(spec.targetPatterns)
        true
      }
      is TargetSpecification.PendingResolution -> false
    }
  }

  fun matches(config: BlazeCommandRunConfiguration, spec: TargetSpecification): Boolean {
    return when (spec) {
      is TargetSpecification.Resolved -> config.singleTargetPattern == spec.targetInfo.label().toString()
      is TargetSpecification.ExplicitPatterns -> config.targetPatterns == spec.targetPatterns
      is TargetSpecification.PendingResolution -> true
    }
  }
}

object CommandApplier {
  fun apply(config: BlazeCommandRunConfiguration, component: CommandComponent) {
    val helper = BlazeCommandRunConfigurationHelper.EP_NAME.extensions.firstOrNull { it.handlesConfiguration(config) }
    if (helper != null) {
      helper.applyCommand(config, component)
    } else {
      val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return
      commonState.commandState.command = component.command
    }
  }

  fun matches(config: BlazeCommandRunConfiguration, component: CommandComponent): Boolean {
    val helper = BlazeCommandRunConfigurationHelper.EP_NAME.extensions.firstOrNull { it.handlesConfiguration(config) }
    if (helper != null) {
      return helper.matchesCommand(config, component)
    }
    val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
    return commonState.commandState.command == component.command
  }
}

object TestFilterApplier {
  fun apply(config: BlazeCommandRunConfiguration, testFilter: TestFilterComponent) {
    val helper = BlazeCommandRunConfigurationHelper.EP_NAME.extensions.firstOrNull { it.handlesConfiguration(config) }
    helper?.applyFilter(config, testFilter)
  }

  fun matches(config: BlazeCommandRunConfiguration, testFilter: TestFilterComponent): Boolean {
    val helper = BlazeCommandRunConfigurationHelper.EP_NAME.extensions.firstOrNull { it.handlesConfiguration(config) }
    return helper?.matchesFilter(config, testFilter) ?: false
  }
}
