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

import com.android.screenshottest.util.UpdateReferenceImagesActionUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testartifacts.screenshot.isScreenshotTestSourceSet
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiDirectory
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils

class UpdateReferenceImagesInDirectoryAction : UpdateReferenceImagesBaseAction(
  UpdateReferenceImagesActionUtils.UPDATE_ACTION_TEXT,
  "Updates the reference images for screenshot tests", AllIcons.FileTypes.Image
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    if(!StudioFlags.ENABLE_SCREENSHOT_TESTING.get()){
      return
    }

    val context = ConfigurationContext.getFromEvent(e)
    val location = context.location ?: return
    val psiElement = location.psiElement
    if (psiElement !is PsiDirectory) {
      return
    }
    val facet = AndroidUtils.getAndroidModule(context)?.let { AndroidFacet.getInstance(it) } ?: return
    e.presentation.isEnabledAndVisible = isScreenshotTestSourceSet(location, facet)
  }
}