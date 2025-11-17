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

import com.android.tools.idea.testartifacts.screenshot.isClassDeclarationWithPreviewTestAnnotatedMethods
import com.android.tools.idea.testartifacts.screenshot.isScreenshotTestSourceSet
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils

class UpdateReferenceImagesInClassAction : UpdateReferenceImagesBaseAction(
  "Add/Update Reference Images",
  "Updates the reference images for screenshot tests in this class.", AllIcons.FileTypes.Image
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false

    val context = ConfigurationContext.getFromEvent(e)
    val location = context.location ?: return
    val psiElement = location.psiElement
    if (psiElement is PsiDirectory) {
      return
    }
    val psiClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass::class.java, false) ?: return
    val facet = AndroidUtils.getAndroidModule(context)?.let { AndroidFacet.getInstance(it) } ?: return
    e.presentation.isEnabledAndVisible = isScreenshotTestSourceSet(location, facet) && isClassDeclarationWithPreviewTestAnnotatedMethods(psiClass)
  }
}