/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.model.primitives.Kind
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.model.primitives.RuleType
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType
import com.google.idea.blaze.base.run.BlazeRunConfigurationFactory
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** Creates run configurations from a BUILD file targets. */
class BlazeBuildFileRunConfigurationProducer :
  BlazeRunConfigurationProducer<BlazeBuildFileRunConfigurationProducer.BuildTarget>(BlazeCommandRunConfigurationType.getInstance()) {

  class BuildTarget(@JvmField val rule: FuncallExpression, @JvmField val ruleType: RuleType, @JvmField val label: Label) :
    RunConfigurationContext {
    override val sourceElement: PsiElement
      get() = rule

    override fun setupRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
      val project = config.project
      val blazeProjectData = BlazeProjectDataManager.getInstance(project).blazeProjectData ?: return false
      setupConfiguration(project, blazeProjectData, config, this)
      return true
    }

    override fun matchesRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
      if (config.targetPatterns != listOf(label.toString())) {
        return false
      }
      val blazeProjectData = BlazeProjectDataManager.getInstance(config.project).blazeProjectData ?: return false
      val generatedConfiguration = BlazeCommandRunConfiguration(config.project, config.factory, config.name)
      setupConfiguration(config.project, blazeProjectData, generatedConfiguration, this)

      // ignore filtered test configs, produced by other configuration producers.
      val handlerState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java)
      if (handlerState?.testFilterFlag != null) {
        return false
      }

      return config.suggestedName() == generatedConfiguration.suggestedName() &&
        config.handler?.commandName == generatedConfiguration.handler?.commandName
    }

    fun guessTargetInfo(): TargetInfo? {
      val ruleName = rule.functionName ?: return null
      val kind = Kind.fromRuleName(ruleName)
      return kind?.let { TargetInfo(label, it.kindString) }
    }
  }

  override fun findContext(context: ConfigurationContext): BlazeBuildFileRunConfigurationProducer.BuildTarget? {
    return getBuildTarget(context)
  }

  companion object {
    private fun getBuildTarget(context: ConfigurationContext): BuildTarget? {
      return getBuildTarget(PsiTreeUtil.getNonStrictParentOfType(context.psiLocation, FuncallExpression::class.java))
    }

    @JvmStatic
    fun getBuildTarget(rule: FuncallExpression?): BuildTarget? {
      if (rule == null) {
        return null
      }
      var ruleName = rule.functionName
      var label = rule.resolveBuildLabel()
      if (ruleName == null || label == null) {
        return null
      }
      // TODO: Finding targets should not be done with the macro name
      // but it should be done based on line number from the blaze project data query.
      if (ruleName == "iml_module") {
        // iml_modules generate one executable target:
        ruleName = "java_test"
        label = Label.create(label.toString() + "_tests")
      }
      return BuildTarget(rule, Kind.guessRuleType(ruleName), label)
    }

    private fun setupConfiguration(
      project: Project,
      blazeProjectData: BlazeProjectData,
      configuration: BlazeCommandRunConfiguration,
      target: BuildTarget,
    ) {
      // First see if a BlazeRunConfigurationFactory can give us a specialized setup.
      for (configurationFactory in BlazeRunConfigurationFactory.EP_NAME.extensions) {
        if (
          configurationFactory.handlesTarget(project, blazeProjectData, target.label) &&
            configurationFactory.handlesConfiguration(configuration)
        ) {
          configurationFactory.setupConfiguration(configuration, target.label)
          return
        }
      }

      // If no factory exists, directly set up the configuration.
      setupBuildFileConfiguration(configuration, target)
    }

    private fun setupBuildFileConfiguration(config: BlazeCommandRunConfiguration, target: BuildTarget) {
      val info = target.guessTargetInfo()
      if (info != null) {
        config.setTargetInfo(info)
      } else {
        config.setTargetPattern(target.label.toString())
      }
      val state = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java)
      if (state != null) {
        state.commandState.command = commandForRuleType(target.ruleType)
      }
      config.setGeneratedName()
    }

    @JvmStatic
    fun commandForRuleType(ruleType: RuleType): BlazeCommandName {
      return when (ruleType) {
        RuleType.BINARY -> BlazeCommandName.RUN
        RuleType.TEST -> BlazeCommandName.TEST
        else -> BlazeCommandName.BUILD
      }
    }
  }
}
