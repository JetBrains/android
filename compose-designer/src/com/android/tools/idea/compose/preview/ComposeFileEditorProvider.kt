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
import com.android.tools.idea.flags.StudioFlags.COMPOSE_PREVIEW_AUTO_BUILD
import com.android.tools.idea.rendering.RenderSettings
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
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

internal fun findComposePreviewManagersForContext(context: DataContext): List<ComposePreviewManager> {
  val project = context.getData(CommonDataKeys.PROJECT) ?: return emptyList()
  val file = context.getData(CommonDataKeys.VIRTUAL_FILE) ?: return emptyList()
  return FileEditorManager.getInstance(project)?.getEditors(file)
    ?.filterIsInstance<ComposeTextEditorWithPreview>()
    ?.map { it.preview } ?: emptyList()
}

/**
 * [ToolbarActionGroups] that includes the [ForceCompileAndRefreshAction]
 */
private class ComposePreviewToolbar(private val surface: DesignSurface) :
  ToolbarActionGroups(surface) {
  private inner class ViewOptionsAction : DropDownAction(message("action.view.options.title"), null, StudioIcons.Common.VISIBILITY_INLINE) {
    init {
      add(ToggleShowDecorationAction())
      add(ToggleAutoBuildAction())
    }
  }

  private inner class ToggleShowDecorationAction :
    ToggleAction(message("action.show.decorations.title"), message("action.show.decorations.description"), null) {

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val settings = RenderSettings.getProjectSettings(surface.project)

      if (settings.showDecorations != state) {
        // We also persist the settings to the RenderSettings
        settings.showDecorations = state
        findComposePreviewManagersForContext(e.dataContext).forEach { it.refresh() }
      }
    }

    override fun isSelected(e: AnActionEvent): Boolean = RenderSettings.getProjectSettings(surface.project).showDecorations
  }

  private inner class ToggleAutoBuildAction :
    ToggleAction(message("action.auto.build.title"), message("action.auto.build.description"), null) {

    override fun update(e: AnActionEvent) {
      super.update(e)

      e.presentation.isEnabledAndVisible = COMPOSE_PREVIEW_AUTO_BUILD.get()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      findComposePreviewManagersForContext(e.dataContext).forEach { it.isAutoBuildEnabled = state }
    }

    override fun isSelected(e: AnActionEvent): Boolean = findComposePreviewManagersForContext(e.dataContext)
      .any { it.isAutoBuildEnabled }
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
  private val filePreviewElementProvider: () -> FilePreviewElementFinder = ::defaultFilePreviewElementFinder) : FileEditorProvider, DumbAware {
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

    val hasPreviewMethods = filePreviewElementProvider().hasPreviewMethods(project, file)
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
    val previewProvider = object: PreviewElementProvider {
      override val previewElements: List<PreviewElement>
        get() = if (DumbService.isDumb(project)) emptyList() else filePreviewElementProvider().findPreviewMethods(project, file)
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