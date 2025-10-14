/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.action

import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.impl.RunManagerImpl
import com.android.screenshottest.ui.UpdateReferenceImagesDialog
import com.android.screenshottest.listener.UpdateScreenshotTestResultsListener
import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

/**
 * Action to add or update the reference images for screenshot tests.
 * This action must be stateless, as the IDE creates a single instance.
 */
class UpdateReferenceImagesAction : AnAction(
  "Add/Update Reference Images",
  "Updates the reference images for screenshot tests.",
  AllIcons.FileTypes.Image
) {

  private val PREVIEW_TEST_ANNOTATION = "com.android.tools.screenshot.PreviewTest"

  override fun actionPerformed(e: AnActionEvent) {
    val context = ConfigurationContext.getFromEvent(e)
    val project = context.project ?: return
    val module = context.module ?: return
    val psiFile = context.location?.psiElement?.containingFile as? KtFile ?: return
    val previewFunctions = findPreviewTestFunctions(psiFile)

    val validateRunconfigSettings = context.createConfigurationsFromContext()?.firstOrNull()?.configurationSettings
                           ?: return
    val updateRunconfigSettings = RunManagerImpl.getInstanceImpl(project).createConfiguration(validateRunconfigSettings.configuration, validateRunconfigSettings.factory)
    updateRunconfigSettings.isTemporary = true
    updateRunconfigSettings.isActivateToolWindowBeforeRun = false
    val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID) ?: return

    val dialog = UpdateReferenceImagesDialog(project, previewFunctions, module, context.psiLocation)

    project.messageBus.connect(dialog.disposable).subscribe(AndroidTestSuiteView.ANDROID_TEST_SUITE_TOPIC, UpdateScreenshotTestResultsListener(dialog))
    ExecutionUtil.runConfiguration(updateRunconfigSettings, executor)
    dialog.show()
  }

  @VisibleForTesting
  fun findPreviewTestFunctions(psiFile: KtFile): List<KtNamedFunction> {
    val visitedAnnotations = mutableMapOf<String, Boolean>()
    return PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java)
      .filter { isPreviewTest(it, visitedAnnotations) }
  }

  @VisibleForTesting
  fun determineTestClassFqns(functions: List<KtNamedFunction>): Set<String> {
    return functions.mapNotNull { function ->
      val containingClass = PsiTreeUtil.getParentOfType(function, KtClass::class.java)
      if (containingClass != null) {
        containingClass.fqName?.asString()
      } else {
        val file = function.containingKtFile
        val packageName = file.packageFqName.asString()
        val className = file.name.removeSuffix(".kt") + "Kt"
        if (packageName.isEmpty()) className else "$packageName.$className"
      }
    }.toSet()
  }

  private fun isPreviewTest(function: KtNamedFunction, visited: MutableMap<String, Boolean>): Boolean {
    val psiMethod = (function.toUElement() as? UMethod)?.javaPsi as? PsiMethod
    return psiMethod != null && psiMethod.annotations.any{ it.qualifiedName == PREVIEW_TEST_ANNOTATION}
  }

  companion object {
    private val LOG = Logger.getInstance(UpdateReferenceImagesAction::class.java)
  }
}