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

import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.dependencies.TestSize
import com.google.idea.blaze.base.model.primitives.RuleType
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import java.io.File

/** Specifies how the Bazel test runner parses --test_filter flags. */
enum class TestFilterSyntax {
  JUNIT_3,
  JUNIT_4,
  RULES_KOTLIN,
}

/** Identifies an individual test entity (class, method, parameter) in pure domain terms, independent of CLI flag syntax. */
data class TestSelector(val className: String, val methodName: String?, val parameterName: String?, val testFilterSyntax: TestFilterSyntax)

/** Encapsulates test selection scope and UI naming hints. Null testFilter indicates target-wide execution of all tests. */
data class TestFilterComponent(val testSelectors: List<TestSelector>, val appendFilteredSuffix: Boolean)

/**
 * Describes Bazel target execution specs based on data available at the current pipeline state: PendingResolution (unresolved source file
 * metadata), Resolved (TargetInfo), or ExplicitPatterns (target labels).
 */
sealed class TargetSpecification {
  /** Unresolved source file context extracted synchronously during Stage 1 without reading the build graph or taking PSI locks. */
  data class PendingResolution(val file: File, val testSize: TestSize?, val ruleType: RuleType) : TargetSpecification()

  /** Fully resolved Bazel target label and rule kind ready for application. */
  data class Resolved(val targetInfo: TargetInfo) : TargetSpecification()

  /** Explicit Bazel target patterns (e.g. //foo/bar/... for directory clicks). */
  data class ExplicitPatterns(val targetPatterns: List<String>) : TargetSpecification()
}

/** Encapsulates Bazel execution verb (TEST vs RUN) and context-specific CLI flag overrides. */
data class CommandComponent(val command: BlazeCommandName, val extraFlags: List<String>)

/** Synchronous Stage 1 domain payload encapsulating execution target, filters, and command verb across the 3-stage pipeline. */
data class UnifiedRunContext(
  override val sourceElement: PsiElement,
  val target: TargetSpecification?,
  val testFilter: TestFilterComponent?,
  val command: CommandComponent?,
) : RunConfigurationContext {

  override fun setupConfigurationName(config: BlazeCommandRunConfiguration) {
    if (testFilter?.appendFilteredSuffix == true) {
      config.name = config.name + " (filtered)"
      config.setNameChangedByUser(true)
    } else if (testFilter != null) {
      val description = UnifiedContextApplier.getFilterDescription(testFilter.testSelectors)
      if (description != null) {
        val nameBuilder = BlazeConfigurationNameBuilder(config)
        command?.command?.let { nameBuilder.setCommandName(it.toString()) }
        nameBuilder.setTargetString(description)
        config.name = nameBuilder.build()
        config.setNameChangedByUser(true)
      } else {
        config.setGeneratedName()
      }
    } else {
      config.setGeneratedName()
    }
  }

  override suspend fun refine(context: ConfigurationContext): RunConfigurationContext {
    var current = this
    for (refiner in UnifiedRunContextRefiner.EP_NAME.extensions) {
      current = refiner.refine(context, current)
    }
    return current
  }

  override fun setupRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
    return UnifiedContextApplier.apply(config, this)
  }

  override fun matchesRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
    return UnifiedContextApplier.matches(config, this)
  }

  override suspend fun resolve(project: Project, context: ConfigurationContext?): RunConfigurationContext {
    val currentTarget = target
    if (currentTarget !is TargetSpecification.PendingResolution) return this
    val resolvedTarget = UnifiedRunContextResolver.resolveTargetSpec(project, currentTarget, context)
    return copy(sourceElement = sourceElement, target = resolvedTarget, testFilter = testFilter, command = command)
  }
}
