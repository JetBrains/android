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
package com.android.tools.idea.common.surface

import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.troubleshooting.TroubleInfoCollector
import com.intellij.util.ui.UIUtil

private fun collectEditorInfo(editor: FileEditor): String =
  UIUtil.invokeAndWaitIfNeeded(
    Computable { "FileEditor: file=${editor.file.name} isModified=${editor.isModified}" }
  )

private fun collectSceneManagerInfo(sceneManagerInfo: SceneManager?): String =
  sceneManagerInfo?.let { sceneManager ->
    """
    ${sceneManager.javaClass.simpleName}:
      renderResult = ${(sceneManager as? LayoutlibSceneManager)?.renderResult}
  """
      .trimIndent()
  }
  ?: ""

private fun collectModelAndSceneManagerInfo(model: NlModel, sceneManager: SceneManager?): String =
  """
    NlModel: name=${model.modelDisplayName} module=${model.module.name}
    ${collectSceneManagerInfo(sceneManager).prependIndent()}
  """
    .trimIndent()

private fun collectSurfaceInfo(surface: DesignSurface<*>?): String {
  if (surface == null) return ""

  val issuePanelService = IssuePanelService.getInstance(surface.project)
  val surfaceContent =
    StringBuilder(
      "${surface.javaClass.simpleName}: issuePanelVisible=${issuePanelService.isIssuePanelVisible(surface)}"
    )

  surface.models
    .map { collectModelAndSceneManagerInfo(it, surface.getSceneManager(it)).prependIndent() }
    .forEach { surfaceContent.appendLine(it) }

  return surfaceContent.toString()
}

/**
 * Collects general information about the editors and design surfaces.
 */
internal class DesignSurfaceTroubleInfoCollector : TroubleInfoCollector {
  override fun collectInfo(project: Project): String {
    val editors = FileEditorManager.getInstance(project).allEditors
    val editorsToSurface =
      editors
        .mapNotNull {
          val surface =
            UIUtil.invokeAndWaitIfNeeded(Computable<DesignSurface<*>> { it.getDesignSurface() })
            ?: return@mapNotNull null
          it to surface
        }
        .toMap()

    return editors.joinToString("\n") {
      """
      ${collectEditorInfo(it)}
      ${collectSurfaceInfo(editorsToSurface[it]).prependIndent()}
    """
        .trimIndent()
    }
  }
}