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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderSettings
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import icons.StudioIcons

internal fun findComposePreviewManagerForAction(e: AnActionEvent): ComposePreviewManager? {
  val project = e.project ?: return null
  val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
  return FileEditorManager.getInstance(project)?.getEditors(file)
    ?.filterIsInstance<ComposeTextEditorWithPreview>()
    ?.map { it.preview }
    ?.firstOrNull()
}

/**
 * [ToolbarActionGroups] that includes the [ForceCompileAndRefreshAction]
 */
private class ComposePreviewToolbar(private val surface: DesignSurface) :
  ToolbarActionGroups(surface) {
  private inner class ViewOptionsAction : DropDownAction(message("action.view.options.title"), null, StudioIcons.Common.VISIBILITY_INLINE) {
    init {
      add(ToggleShowDecorationAction())
    }
  }

  private inner class ToggleShowDecorationAction :
    ToggleAction(message("action.show.decorations.title"), message("action.show.decorations.description"), null) {

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val settings = RenderSettings.getProjectSettings(surface.project)

      if (settings.showDecorations != state) {
        // We also persist the settings to the RenderSettings
        settings.showDecorations = state
        findComposePreviewManagerForAction(e)?.refresh()
      }
    }

    override fun isSelected(e: AnActionEvent): Boolean = RenderSettings.getProjectSettings(surface.project).showDecorations
  }

  override fun getNorthGroup(): ActionGroup = DefaultActionGroup(listOf(
    ViewOptionsAction(),
    ForceCompileAndRefreshAction(surface)
  ))

  override fun getNorthEastGroup(): ActionGroup = DefaultActionGroup().apply {
    addAll(getZoomActionsWithShortcuts(surface, this@ComposePreviewToolbar))
    add(IssueNotificationAction(surface))
  }
}

internal class ComposeTextEditorWithPreview constructor(
  composeTextEditor: TextEditor,
  preview: PreviewEditor) :
  SeamlessTextEditorWithPreview<PreviewEditor>(composeTextEditor, preview, "Compose Editor") {
  init {
    preview.registerShortcuts(component)
  }
}

/**
 * Returns the Compose [PreviewEditor] or null if this [FileEditor] is not a Compose preview.
 */
fun FileEditor.getComposePreviewManager(): ComposePreviewManager? = when (this) {
  is PreviewEditor -> this
  else -> (this as? ComposeTextEditorWithPreview)?.preview
}

/**
 * Provider for Compose Preview editors.
 */
class ComposeFileEditorProvider @JvmOverloads constructor(
  private val previewElementProvider: () -> PreviewElementFinder = ::defaultPreviewElementFinder) : FileEditorProvider, DumbAware {
  private val LOG = Logger.getInstance(ComposeFileEditorProvider::class.java)

  private object ComposeEditorFileType : LayoutEditorFileType() {
    override fun getLayoutEditorStateType() = LayoutEditorState.Type.COMPOSE

    override fun isResourceTypeOf(file: PsiFile): Boolean =
      file.virtualFile is ComposeAdapterLightVirtualFile

    override fun getToolbarActionGroups(surface: DesignSurface): ToolbarActionGroups =
      ComposePreviewToolbar(surface)

    override fun getSelectionContextToolbar(surface: DesignSurface, selection: List<NlComponent>): DefaultActionGroup =
      DefaultActionGroup()
  }

  init {
    if (StudioFlags.COMPOSE_PREVIEW.get()) {
      DesignerTypeRegistrar.register(ComposeEditorFileType)
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!StudioFlags.COMPOSE_PREVIEW.get() || !file.isKotlinFileType()) {
      return false
    }

    val hasPreviewMethods = previewElementProvider().hasPreviewMethods(project, file)
    if (LOG.isDebugEnabled) {
      LOG.debug("${file.path} hasPreviewMethods=${hasPreviewMethods}")
    }

    return hasPreviewMethods
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (LOG.isDebugEnabled) {
      LOG.debug("createEditor file=${file.path}")
    }
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    val previewProvider = {
      if (DumbService.isDumb(project)) emptyList() else previewElementProvider().findPreviewMethods(project, file)
    }
    val previewEditor = PreviewEditor(psiFile = psiFile, previewProvider = previewProvider)
    val composeEditorWithPreview = ComposeTextEditorWithPreview(textEditor, previewEditor)

    previewEditor.onRefresh = {
      composeEditorWithPreview.isPureTextEditor = previewEditor.previewElements.isEmpty()
    }

    return composeEditorWithPreview
  }

  override fun getEditorTypeId() = "ComposeEditor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}