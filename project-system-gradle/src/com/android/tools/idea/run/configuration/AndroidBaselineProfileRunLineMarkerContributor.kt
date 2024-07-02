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
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.kotlin.getQualifiedName
import com.android.tools.idea.kotlin.hasAnnotation
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.resolve
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManagerEx
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.ActionGroupWrapper
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.isUnderKotlinSourceRootTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import javax.swing.Icon

class BaselineProfileRunLineMarkerContributor : RunLineMarkerContributor() {

  companion object {
    private const val FQ_NAME_ORG_JUNIT_RULE = "org.junit.Rule"
    private const val NAME_ANDROIDX_JUNIT_BASELINE_PROFILE_RULE = "androidx/benchmark/macro/junit4/BaselineProfileRule"
    private const val NAME_ANDROIDX_JUNIT_MACROBENCHMARK_RULE = "androidx/benchmark/macro/junit4/MacrobenchmarkRule"
    private const val FQ_NAME_ANDROIDX_JUNIT_BASELINE_PROFILE_RULE = "androidx.benchmark.macro.junit4.BaselineProfileRule"
    private const val FQ_NAME_ANDROIDX_JUNIT_MACROBENCHMARK_RULE = "androidx.benchmark.macro.junit4.MacrobenchmarkRule"

    private val generateAction = ActionManager.getInstance().getAction("AndroidX.BaselineProfile.RunGenerate")

    private val executorActions: List<AnAction> = ExecutorAction.getActionList()
      .mapNotNull { it as? ExecutorAction }
      .filter { it.executor == DefaultRunExecutor.getRunExecutorInstance() ||
                it.executor == DefaultDebugExecutor.getDebugExecutorInstance() }

    private val createRunConfigAction = ExecutorAction.getActionList()
      .mapNotNull { it as? ActionGroupWrapper }
      .firstOrNull { it.delegate == ActionManager.getInstance().getAction("CreateRunConfiguration") }

    internal fun isKtTestClassIdentifier(e: PsiElement): Boolean {
      if (e.node?.elementType != KtTokens.IDENTIFIER) {
        return false
      }

      val declaration = e.getStrictParentOfType<KtNamedDeclaration>()?.takeIf { it.nameIdentifier == e } ?: return false

      return declaration is KtClassOrObject &&
             declaration.isUnderKotlinSourceRootTypes() &&
             e.parent is KtClass
    }

    internal fun isKtTestMethodIdentifier(e: PsiElement): Boolean {
      if (e.node?.elementType != KtTokens.IDENTIFIER) {
        return false
      }

      val declaration = e.getStrictParentOfType<KtNamedDeclaration>()?.takeIf { it.nameIdentifier == e } ?: return false

      return declaration is KtNamedFunction &&
             declaration.isUnderKotlinSourceRootTypes() &&
             e.parent is KtNamedFunction &&
             declaration.hasAnnotation(ClassId.fromString("org/junit/Test"))
    }

    internal fun isJavaTestClassIdentifier(e: PsiElement): Boolean {
      return e is PsiIdentifier && e.parent is PsiClass
    }

    internal fun isJavaTestMethodIdentifier(e: PsiElement): Boolean {
      return e is PsiIdentifier &&
             e.parent is PsiMethod &&
             AnnotationUtil.findAnnotation(e.parent as PsiMethod, "org.junit.Test") != null
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    internal fun anyTopLevelKtRule(psiElement: PsiElement): String? {
      // Find class body
      val topLevelClass = if (psiElement is KtClass) {
        psiElement
      }
      else {
        PsiTreeUtil.getParentOfType(psiElement, KtClass::class.java) ?: return null
      }

      // Find properties
      val ktProperties = PsiTreeUtil.findChildrenOfType(topLevelClass, KtProperty::class.java).toList()
      if (ktProperties.isEmpty()) {
        return null
      }

      // Analyzes the class to check each property to see if there is at least a BaselineProfileRule applied.
      return allowAnalysisOnEdt {
        @OptIn(KtAllowAnalysisFromWriteAction::class) // TODO(b/310045274)
        allowAnalysisFromWriteAction {
          analyze(topLevelClass) {
            ktProperties
              .filter { prop ->
                // Check that this property has a rule annotation applied and that the parent class node is the
                // same of the method (to ensure both method and rule are in the same class).
                prop.annotationEntries.any { it.getQualifiedName() == FQ_NAME_ORG_JUNIT_RULE } &&
                PsiTreeUtil.getParentOfType(prop, KtClass::class.java) == topLevelClass
              }.firstNotNullOfOrNull { prop ->
                // TODO(b/303222395): Only using the receiver type here, but this won't work if the baseline profile rule
                // gets extended.
                PsiTreeUtil
                  .findChildOfType(prop, KtCallExpression::class.java)
                  ?.getKtType()
                  ?.asStringForDebugging()
                  ?.takeIf { it == NAME_ANDROIDX_JUNIT_BASELINE_PROFILE_RULE || it == NAME_ANDROIDX_JUNIT_MACROBENCHMARK_RULE }
              }
          }
        }
      }
    }

    internal fun anyTopLevelJavaRule(psiElement: PsiElement, vararg filter: String): String? {
      // Find class body
      val topLevelClass = if (psiElement is PsiClass) {
        psiElement
      }
      else {
        PsiTreeUtil.getParentOfType(psiElement, PsiClass::class.java) ?: return null
      }

      // Find class members
      val classMembers = PsiTreeUtil.getChildrenOfTypeAsList(topLevelClass, PsiField::class.java)
      if (classMembers.isEmpty()) {
        return null
      }

      // Find a class member that has a BaselineProfileRule applied.
      for (member : PsiMember in classMembers) {
        // Only evaluate direct field member of the top level class
        val rule = PsiTreeUtil
          .findChildrenOfType(member, PsiAnnotation::class.java)
          .filter {
            it.resolveAnnotationType()?.qualifiedName == FQ_NAME_ORG_JUNIT_RULE
          }
          .map {
            PsiTreeUtil.findChildOfType(member, PsiNewExpression::class.java)
              ?.type
              .resolve()
              ?.qualifiedName
              ?.takeIf { it == FQ_NAME_ANDROIDX_JUNIT_BASELINE_PROFILE_RULE || it == FQ_NAME_ANDROIDX_JUNIT_MACROBENCHMARK_RULE }
          }
          .firstOrNull()

        if (rule != null) {
          return rule
        }
      }
      return null
    }
  }

  private val generateBaselineProfileInfo = createOverridingInfo(
    AllIcons.RunConfigurations.TestState.Run_run,
    AndroidBundle.message("android.run.configuration.generate.baseline.profile"),
    listOfNotNull(
      generateAction,
      Separator.getInstance().takeIf { executorActions.isNotEmpty() },
      *executorActions.toTypedArray(),
      createRunConfigAction.takeIf { executorActions.isNotEmpty() && createRunConfigAction != null })
  )

  private val runTestInfo = createOverridingInfo(
    AllIcons.RunConfigurations.TestState.Run,
    AndroidBundle.message("android.run.configuration.generate.baseline.profile"),
    listOfNotNull(
      *executorActions.toTypedArray(),
      createRunConfigAction.takeIf { executorActions.isNotEmpty() && createRunConfigAction != null })
  )

  private fun createOverridingInfo(
    icon: Icon,
    message: String,
    actions: List<AnAction>): Info {
    return object: Info(
      icon,
      actions.toTypedArray(),
      { _ -> message }
    ) {
      override fun shouldReplace(other: Info): Boolean {
        return other.actions.intersect(executorActions.toSet()).isNotEmpty()
      }
    }
  }

  override fun getInfo(e: PsiElement): Info? {
    // If the studio flag is not enabled, skip entirely.
    if (!StudioFlags.GENERATE_BASELINE_PROFILE_GUTTER_ICON.get()) return null

    if (e.project.getSyncManager().isSyncNeeded()) return null

    var classIdentifier = false
    var rule: String? = null

    if (e.language == KotlinLanguage.INSTANCE) {
      classIdentifier = isKtTestClassIdentifier(e)
      val methodIdentifier = isKtTestMethodIdentifier(e)
      if (!classIdentifier && !methodIdentifier) {
        return null
      }
      // This check is potentially computationally expensive, but needs to be checked if when
      // either this PsiElement is a class or a method. Therefore, we check it only once after
      // making sure we have a class or method identifier.
      rule = anyTopLevelKtRule(e)
    }
    else if (e.language == JavaLanguage.INSTANCE) {
      classIdentifier = isJavaTestClassIdentifier(e)
      val methodIdentifier = isJavaTestMethodIdentifier(e)
      if (!classIdentifier && !methodIdentifier) {
        return null
      }
      // This check is potentially computationally expensive, but needs to be checked if when
      // either this PsiElement is a class or a method. Therefore, we check it only once after
      // making sure we have a class or method identifier.
      rule = anyTopLevelJavaRule(e)
    }

    return when (rule) {
      NAME_ANDROIDX_JUNIT_BASELINE_PROFILE_RULE, FQ_NAME_ANDROIDX_JUNIT_BASELINE_PROFILE_RULE ->
        if (classIdentifier) generateBaselineProfileInfo else runTestInfo
      NAME_ANDROIDX_JUNIT_MACROBENCHMARK_RULE, FQ_NAME_ANDROIDX_JUNIT_MACROBENCHMARK_RULE -> runTestInfo
      else -> null
    }
  }
}

class BaselineProfileAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val sourceModule = e.getData(PlatformCoreDataKeys.MODULE) ?: return
    val targetModulePath = GradleAndroidModel.get(sourceModule)?.selectedVariant?.testedTargetVariants?.map { it.targetProjectPath }?.firstOrNull() ?: return
    val targetModuleGradlePath = sourceModule.getGradleProjectPath()?.resolve(targetModulePath)
    val runManager = RunManagerEx.getInstanceEx(project)
    val runConfiguration = runManager.allSettings
      .asSequence()
      .filter { it.type == AndroidBaselineProfileRunConfigurationType.getInstance() }
      .filter { (it.configuration as AndroidBaselineProfileRunConfiguration).configurationModule.module?.getGradleProjectPath() == targetModuleGradlePath }
      .firstOrNull()
      .let {
        // If the configuration was found, use this one
        if (it != null) return@let it

        val runnerAndConfigSettings = runManager.createConfiguration(
          AndroidBaselineProfileRunConfigurationType.NAME,
          AndroidBaselineProfileRunConfigurationType.getInstance().factory
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

