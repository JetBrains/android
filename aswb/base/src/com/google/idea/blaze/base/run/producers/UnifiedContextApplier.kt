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
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState

/** Orchestrates Stage 3 application and matching of UnifiedRunContext to BlazeCommandRunConfiguration. */
object UnifiedContextApplier {
  fun apply(config: BlazeCommandRunConfiguration, context: UnifiedRunContext): Boolean {
    var applied = false

    // 1. Common Application: Command & Target (Target setting triggers config.updateHandler())
    context.command?.let {
      CommandApplier.apply(config, it)
      applied = true
    }
    context.target?.let {
      TargetApplier.apply(config, it)
      applied = true
    }

    // 2. Handler-Specific Application: FilterApplier operates AFTER TargetApplier has instantiated config.handler
    context.filter?.let {
      FilterApplier.apply(config, it)
      applied = true
    }

    if (context.filter?.appendFilteredSuffix == true) {
      config.name = config.name + " (filtered)"
      config.setNameChangedByUser(true)
    } else if (applied) {
      config.setGeneratedName()
    }
    return applied
  }

  fun matches(config: BlazeCommandRunConfiguration, context: UnifiedRunContext): Boolean {
    context.command?.let { if (!CommandApplier.matches(config, it)) return false }
    context.target?.let { if (!TargetApplier.matches(config, it)) return false }

    if (context.filter != null) {
      if (!FilterApplier.matches(config, context.filter)) return false
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
  fun apply(config: BlazeCommandRunConfiguration, spec: TargetSpecification) {
    when (spec) {
      is TargetSpecification.Resolved -> config.setTargetInfo(spec.targetInfo)
      is TargetSpecification.ExplicitPatterns -> config.setTargetPatterns(spec.targetPatterns)
      is TargetSpecification.PendingResolution -> {
        val resolved = UnifiedRunContextResolver.resolveTargetSpec(config.project, spec)
        if (resolved is TargetSpecification.Resolved) {
          config.setTargetInfo(resolved.targetInfo)
        }
      }
    }
  }

  fun matches(config: BlazeCommandRunConfiguration, spec: TargetSpecification): Boolean {
    return when (spec) {
      is TargetSpecification.Resolved -> config.singleTargetPattern == spec.targetInfo.label().toString()
      is TargetSpecification.ExplicitPatterns -> config.targetPatterns == spec.targetPatterns
      is TargetSpecification.PendingResolution -> {
        val resolved = UnifiedRunContextResolver.resolveTargetSpec(config.project, spec)
        if (resolved is TargetSpecification.Resolved) config.singleTargetPattern == resolved.targetInfo.label().toString() else false
      }
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

object FilterApplier {
  fun apply(config: BlazeCommandRunConfiguration, component: FilterComponent) {
    val helper = BlazeCommandRunConfigurationHelper.EP_NAME.extensions.firstOrNull { it.handlesConfiguration(config) }
    helper?.applyFilter(config, component)
  }

  fun matches(config: BlazeCommandRunConfiguration, component: FilterComponent): Boolean {
    val helper = BlazeCommandRunConfigurationHelper.EP_NAME.extensions.firstOrNull { it.handlesConfiguration(config) }
    return helper?.matchesFilter(config, component) ?: false
  }
}
