/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.requestBuild
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.ui.JBColor

private val GREEN_REFRESH_BUTTON = ColoredIconGenerator.generateColoredIcon(AllIcons.Actions.ForceRefresh,
                                                                            JBColor(0x59A869, 0x499C54))

internal fun requestBuildForSurface(surface: DesignSurface) =
  surface.models.map { it.module }.distinct()
    .onEach {
      requestBuild(surface.project, it)
    }
    .isNotEmpty()

/**
 * [AnAction] that triggers a compilation of the current module. The build will automatically trigger a refresh
 * of the surface.
 */
internal class ForceCompileAndRefreshAction(private val surface: DesignSurface) :
  AnAction(message("action.build.and.refresh.title"), null, GREEN_REFRESH_BUTTON) {
  override fun actionPerformed(e: AnActionEvent) {
    if (!requestBuildForSurface(surface)) {
      // If there are no models in the surface, we can not infer which models we should trigger
      // the build for. The fallback is to find the module for the editor and trigger that.
      LangDataKeys.MODULE.getData(e.dataContext)?.let {
        requestBuild(surface.project, it)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val isRefreshing = findComposePreviewManagersForContext(e.dataContext).any { it.status().isRefreshing }
    presentation.isEnabled = !isRefreshing
  }
}