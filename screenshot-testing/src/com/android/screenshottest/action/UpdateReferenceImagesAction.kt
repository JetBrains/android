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

import com.android.screenshottest.ScreenshotTestBuildSystemAdapter
import com.android.screenshottest.ui.UpdateReferenceImagesDialog
import com.android.screenshottest.util.ScreenshotTestRunner
import com.android.screenshottest.util.TestResultParser
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import org.jetbrains.android.util.AndroidUtils
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
    val module = AndroidUtils.getAndroidModule(context) ?: return
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return
    val triggerElement: PsiElement? = context.location?.psiElement

    val previewFunctions = findPreviewTestFunctions(psiFile)
    if (previewFunctions.isEmpty()) {
      return
    }

    val testClassFqns = determineTestClassFqns(previewFunctions)
    if (testClassFqns.isEmpty()) {
      LOG.warn("Could not determine fully qualified names for test classes.")
      return
    }

    val dialog = ApplicationManager.getApplication().runReadAction<UpdateReferenceImagesDialog> {
      UpdateReferenceImagesDialog(project, previewFunctions, module, triggerElement)
    }

    val taskRunner = ScreenshotTestRunner(project, module)
    val callback = object : TaskCallback {
      override fun onSuccess() = onTaskFinished(module, dialog)
      override fun onFailure() {
        LOG.warn("Screenshot 'validate' task finished with a failure, which is expected. Parsing results.")
        onTaskFinished(module, dialog)
      }
    }

    taskRunner.run(testClassFqns, callback)
    dialog.show()
  }

  private fun onTaskFinished(module: Module, dialog: UpdateReferenceImagesDialog) {
    ApplicationManager.getApplication().invokeLater {
      LOG.info("Screenshot 'validate' task finished. Refreshing VFS before parsing results.")

      val projectSystem = ScreenshotTestBuildSystemAdapter.EP_NAME.extensionList.firstOrNull()
      if (projectSystem == null) {
        LOG.error("ScreenshotTestBuildSystemAdapter extension not found. Cannot refresh VFS.")
        dialog.onBuildFinished(TestResultParser.parse(module))
        return@invokeLater
      }

      val modulePathStr = projectSystem.getLinkedExternalProjectPath(module)
      if (modulePathStr == null) {
        LOG.error("Could not get module project path for ${module.name}. Cannot refresh VFS.")
        dialog.onBuildFinished(TestResultParser.parse(module))
        return@invokeLater
      }

      val buildDir = File(modulePathStr, "build")
      val screenshotOutputDir = buildDir.resolve("outputs/screenshotTest-results/preview")

      LocalFileSystem.getInstance().refreshIoFiles(listOf(screenshotOutputDir), true, true) {
        LOG.info("VFS refresh complete. Parsing test results.")
        val testResults = TestResultParser.parse(module)
        dialog.onBuildFinished(testResults)
      }
    }
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