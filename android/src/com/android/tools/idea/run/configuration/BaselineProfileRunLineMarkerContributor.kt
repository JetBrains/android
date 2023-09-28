/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManagerEx
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.debugger.sequence.psi.callName
import org.jetbrains.kotlin.idea.structuralsearch.resolveReceiverType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getAnnotationEntries
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration


class BaselineProfileRunLineMarkerContributor : RunLineMarkerContributor() {

  companion object {
    private const val FQ_NAME_ORG_JUNIT_TEST = "org.junit.Test"
    private const val FQ_NAME_ORG_JUNIT_RULE = "org.junit.Rule"
    private const val NAME_ANDROIDX_JUNIT_BASELINE_PROFILE_RULE = "androidx/benchmark/macro/junit4/BaselineProfileRule"

  }

  private val actions by lazy(LazyThreadSafetyMode.PUBLICATION) {
    arrayOf(
      ActionManager.getInstance().getAction("AndroidX.BaselineProfile.RunGenerate"))
  }

  override fun getInfo(e: PsiElement): Info? {

    // If the studio flag is not enabled, skip entirely.
    if (!StudioFlags.GENERATE_BASELINE_PROFILE_GUTTER_ICON.get()) return null

    // Process only functions and classes
    return when (e.node.elementType) {
      KtTokens.FUN_KEYWORD -> {
        val ktNamedFunction = (e.parent as? KtNamedFunction) ?: return null
        if (isAnnotatedWithTest(ktNamedFunction) && doesClassHaveBaselineProfileRule(e)) {
          Info(AllIcons.RunConfigurations.TestState.Run, actions) {
            AndroidBundle.message("android.run.configuration.generate.baseline.profile")
          }
        }
        else null
      }

      KtTokens.CLASS_KEYWORD -> {
        if (e.getPsiClass() != null && doesClassHaveBaselineProfileRule(e)) {
          Info(AllIcons.RunConfigurations.TestState.Run_run, actions) {
            AndroidBundle.message("android.run.configuration.generate.baseline.profile")
          }
        }
        else null
      }

      else -> null
    }
  }

  private fun isAnnotatedWithTest(ktNamedFunction: KtNamedFunction): Boolean = analyze(ktNamedFunction) {
    ktNamedFunction.annotationEntries.any { getQualifiedName(it) == FQ_NAME_ORG_JUNIT_TEST }
  }

  private fun doesClassHaveBaselineProfileRule(psiElement: PsiElement): Boolean {

    // Find class body
    val topLevelClass = if (psiElement is KtClass) {
      psiElement
    }
    else {
      PsiTreeUtil.getParentOfType(psiElement, KtClass::class.java) ?: return false
    }

    // Find properties
    val ktProperties = PsiTreeUtil.findChildrenOfType(topLevelClass, KtProperty::class.java).toList()
    if (ktProperties.isEmpty()) {
      return false
    }

    // Analyzes the class to check each property to see if there is at least a BaselineProfileRule applied.
    return analyze(topLevelClass) {
      ktProperties
        .any { prop ->

          // Check that this property has a rule annotation applied.
          val isRule = prop
            .annotationEntries
            .any { getQualifiedName(it) == FQ_NAME_ORG_JUNIT_RULE }

          // TODO(b/303222395): Only using the receiver type here, but this won't work if the baseline profile rule
          // gets extended.
          val isBaselineProfileCallExpression = PsiTreeUtil
            .findChildOfType(prop, KtCallExpression::class.java)
            ?.getExpectedType()
            ?.asStringForDebugging() == NAME_ANDROIDX_JUNIT_BASELINE_PROFILE_RULE

            // Check that the parent class node is the same of the method (to ensure both method and rule are in the same class).
          val isInSameClassOfMethod = PsiTreeUtil.getParentOfType(prop, KtClass::class.java) == topLevelClass

          // All the three conditions have to be true.
          isRule && isBaselineProfileCallExpression && isInSameClassOfMethod
        }
    }
  }
}

class BaselineProfileAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {

    val project = e.project ?: return
    val configurationType = GradleExternalTaskConfigurationType.getInstance()

    val runManager = RunManagerEx.getInstanceEx(project)
    val runConfiguration = runManager.findConfigurationByTypeAndName(configurationType,
                                                                     BaselineProfileConfigurationProducer.CONFIGURATION_NAME)
      .let {

        // If the configuration was found, use this one
        if (it != null) return@let it

        // Otherwise create a new one
        val configurationContext = ConfigurationContext.getFromContext(e.dataContext, ActionPlaces.EDITOR_GUTTER_POPUP)
        val module = AndroidUtils.getAndroidModule(configurationContext) ?: return

        val runnerAndConfigSettings = runManager.createConfiguration(
          BaselineProfileConfigurationProducer.CONFIGURATION_NAME,
          BaselineProfileConfigurationProducer.factory
        )
        BaselineProfileConfigurationProducer.configure(
          configuration = (runnerAndConfigSettings.configuration) as GradleRunConfiguration,
          module = module
        )
        runManager.addConfiguration(runnerAndConfigSettings)
        return@let runnerAndConfigSettings
      }

    // If the configuration is not selected, if fails says it cannot run on the selected target.
    // Selecting this gradle configuration also disables the target selection.
    runManager.selectedConfiguration = runConfiguration
    ProgramRunnerUtil.executeConfiguration(runConfiguration, DefaultRunExecutor.getRunExecutorInstance())
  }
}

