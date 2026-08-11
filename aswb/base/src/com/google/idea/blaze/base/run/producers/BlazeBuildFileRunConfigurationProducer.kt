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
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression
import com.google.idea.blaze.base.model.primitives.Kind
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.model.primitives.RuleType
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.TestOnly

/** Creates run configurations from BUILD file targets. */
class BlazeBuildFileRunConfigurationProducer :
  BlazeRunConfigurationProducer<UnifiedRunContext>(BlazeCommandRunConfigurationType.getInstance()) {

  override fun findContext(context: ConfigurationContext): UnifiedRunContext? {
    val rule = PsiTreeUtil.getNonStrictParentOfType(context.psiLocation, FuncallExpression::class.java) ?: return null
    return getContextFromRule(rule)
  }

  data class TargetData(@JvmField val ruleType: RuleType, @JvmField val label: Label)

  companion object {
    @TestOnly
    @JvmStatic
    fun getInstance(): BlazeBuildFileRunConfigurationProducer {
      return RunConfigurationProducer.getInstance(BlazeBuildFileRunConfigurationProducer::class.java)
    }

    @JvmStatic
    fun getTargetData(rule: FuncallExpression?): TargetData? {
      if (rule == null) {
        return null
      }
      var ruleName = rule.functionName
      var label = rule.resolveBuildLabel()
      if (ruleName == null || label == null) {
        return null
      }
      if (ruleName == "iml_module") {
        ruleName = "java_test"
        label = Label.create(label.toString() + "_tests")
      }
      return TargetData(Kind.guessRuleType(ruleName), label)
    }

    @JvmStatic
    fun getContextFromRule(rule: FuncallExpression?): UnifiedRunContext? {
      val data = getTargetData(rule) ?: return null
      val command = commandForRuleType(data.ruleType)
      return UnifiedRunContext(
        sourceElement = rule!!,
        target = TargetSpecification.ExplicitPatterns(listOf(data.label.toString())),
        testFilter = null,
        command = CommandComponent(command, emptyList()),
      )
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
