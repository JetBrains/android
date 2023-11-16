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

import com.android.flags.ifEnabled
import com.android.tools.idea.actions.ColorBlindModeAction
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.compose.preview.actions.ComposeFilterShowHistoryAction
import com.android.tools.idea.compose.preview.actions.ComposeFilterTextAction
import com.android.tools.idea.compose.preview.actions.ComposeNotificationGroup
import com.android.tools.idea.compose.preview.actions.ComposeViewControlAction
import com.android.tools.idea.compose.preview.actions.ComposeViewSingleWordFilter
import com.android.tools.idea.compose.preview.actions.GroupSwitchAction
import com.android.tools.idea.compose.preview.actions.ShowDebugBoundaries
import com.android.tools.idea.compose.preview.actions.StopAnimationInspectorAction
import com.android.tools.idea.compose.preview.actions.StopUiCheckPreviewAction
import com.android.tools.idea.compose.preview.actions.UiCheckDropDownAction
import com.android.tools.idea.compose.preview.actions.visibleOnlyInComposeDefaultPreview
import com.android.tools.idea.compose.preview.actions.visibleOnlyInUiCheck
import com.android.tools.idea.compose.preview.essentials.ComposePreviewEssentialsModeManager
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.StopInteractivePreviewAction
import com.android.tools.idea.preview.actions.visibleOnlyInStaticPreview
import com.android.tools.idea.preview.modes.PREVIEW_LAYOUT_GALLERY_OPTION
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.representation.CommonRepresentationEditorFileType
import com.android.tools.idea.preview.representation.InMemoryLayoutVirtualFile
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility.FULL
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility.HIDDEN
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility.SPLIT
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.android.tools.idea.uibuilder.surface.LayoutManagerSwitcher
import com.android.tools.preview.ComposePreviewElementInstance
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.android.uipreview.AndroidEditorSettings.EditorMode
import org.jetbrains.annotations.TestOnly

/** [ToolbarActionGroups] that includes the actions that can be applied to Compose Previews. */
private class ComposePreviewToolbar(surface: DesignSurface<*>) : ToolbarActionGroups(surface) {

  override fun getNorthGroup(): ActionGroup = ComposePreviewNorthGroup()

  private inner class ComposePreviewNorthGroup :
    DefaultActionGroup(
      listOfNotNull(
        StopInteractivePreviewAction(forceDisable = { isPreviewRefreshing(it.dataContext) }),
        StopAnimationInspectorAction(),
        StopUiCheckPreviewAction(),
        StudioFlags.COMPOSE_VIEW_FILTER.ifEnabled { ComposeFilterShowHistoryAction() },
        StudioFlags.COMPOSE_VIEW_FILTER.ifEnabled {
          ComposeFilterTextAction(ComposeViewSingleWordFilter())
        },
        // TODO(b/292057010) Enable group filtering for Gallery mode.
        GroupSwitchAction().visibleOnlyInComposeDefaultPreview(),
        ComposeViewControlAction(
            layoutManagers = PREVIEW_LAYOUT_MANAGER_OPTIONS,
            isSurfaceLayoutActionEnabled = {
              !isPreviewRefreshing(it.dataContext) &&
                // If Essentials Mode is enabled, it should not be possible to switch layout.
                !ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
            },
            updateMode = { selectedOption, manager ->
              if (selectedOption == PREVIEW_LAYOUT_GALLERY_OPTION) {
                // If turning on Gallery layout option - it should be set in preview.
                // TODO (b/292057010) If group filtering is enabled - first element in this group
                // should be selected.
                val element = manager.allPreviewElementsInFileFlow.value.firstOrNull()
                manager.setMode(PreviewMode.Gallery(element))
              } else if (manager.mode.value is PreviewMode.Gallery) {
                // When switching from Gallery mode to Default layout mode - need to set back
                // Default preview mode.
                manager.setMode(PreviewMode.Default(selectedOption))
              } else {
                manager.setMode(manager.mode.value.deriveWithLayout(selectedOption))
              }
            },
            additionalActionProvider = ColorBlindModeAction()
          )
          .visibleOnlyInStaticPreview(),
        Separator.getInstance().visibleOnlyInUiCheck(),
        UiCheckDropDownAction().visibleOnlyInUiCheck(),
        ComposeViewControlAction(
            layoutManagers = BASE_LAYOUT_MANAGER_OPTIONS,
            isSurfaceLayoutActionEnabled = {
              !isPreviewRefreshing(it.dataContext) &&
                // If Essentials Mode is enabled, it should not be possible to switch layout.
                !ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
            },
            updateMode = { selectedOption, manager ->
              manager.setMode(manager.mode.value.deriveWithLayout(selectedOption))
            },
          )
          .visibleOnlyInUiCheck(),
        StudioFlags.COMPOSE_DEBUG_BOUNDS.ifEnabled { ShowDebugBoundaries() },
      ),
    ) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      super.update(e)
      if (isEssentialsModeSelected != ComposePreviewEssentialsModeManager.isEssentialsModeEnabled) {
        isEssentialsModeSelected = ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
        if (isEssentialsModeSelected) {
          val layoutSwitcher =
            (e.getData(DESIGN_SURFACE)?.sceneViewLayoutManager as? LayoutManagerSwitcher)
          ApplicationManager.getApplication().invokeLater {
            layoutSwitcher?.setLayoutManager(
              PREVIEW_LAYOUT_GALLERY_OPTION.layoutManager,
              PREVIEW_LAYOUT_GALLERY_OPTION.sceneViewAlignment
            )
          }
        }
      }
    }

    private var isEssentialsModeSelected: Boolean = false
  }

  override fun getNorthEastGroup(): ActionGroup = ComposeNotificationGroup(this)
}

/** [InMemoryLayoutVirtualFile] for composable functions. */
class ComposeAdapterLightVirtualFile(
  name: String,
  content: String,
  originFileProvider: () -> VirtualFile?
) : InMemoryLayoutVirtualFile("compose-$name", content, originFileProvider)

/** A [PreviewRepresentationProvider] coupled with [ComposePreviewRepresentation]. */
class ComposePreviewRepresentationProvider(
  private val filePreviewElementProvider: () -> FilePreviewElementFinder =
    ::defaultFilePreviewElementFinder
) : PreviewRepresentationProvider {

  private object ComposeEditorFileType :
    CommonRepresentationEditorFileType(
      ComposeAdapterLightVirtualFile::class.java,
      LayoutEditorState.Type.COMPOSE,
      ::ComposePreviewToolbar
    )

  init {
    DesignerTypeRegistrar.register(ComposeEditorFileType)
  }

  /**
   * Checks if the input [psiFile] contains compose previews and therefore can be provided with the
   * `PreviewRepresentation` of them.
   */
  override suspend fun accept(project: Project, psiFile: PsiFile): Boolean =
    psiFile.virtualFile.isKotlinFileType() &&
      (readAction { psiFile.getModuleSystem()?.usesCompose ?: false && !psiFile.isInLibrary() })

  /** Creates a [ComposePreviewRepresentation] for the input [psiFile]. */
  override fun createRepresentation(psiFile: PsiFile): ComposePreviewRepresentation {
    val hasComposableMethods =
      filePreviewElementProvider().hasComposableMethods(psiFile.project, psiFile.virtualFile)
    val hasPreviewMethods =
      filePreviewElementProvider().hasPreviewMethods(psiFile.project, psiFile.virtualFile)
    thisLogger().debug { "${psiFile.virtualFile.path} hasPreviewMethods=${hasPreviewMethods}" }

    val globalState = AndroidEditorSettings.getInstance().globalState
    val preferredVisibility =
      when {
        hasPreviewMethods -> globalState.preferredPreviewableEditorMode.getVisibility(SPLIT)
        hasComposableMethods -> globalState.preferredComposableEditorMode.getVisibility(HIDDEN)
        else -> globalState.preferredKotlinEditorMode.getVisibility(HIDDEN)
      }

    return ComposePreviewRepresentation(psiFile, preferredVisibility, ::ComposePreviewViewImpl)
  }

  override val displayName = message("representation.name")

  private fun PsiFile.isInLibrary() =
    ProjectRootManager.getInstance(project).fileIndex.isInLibrary(virtualFile)
}

private const val PREFIX = "ComposePreview"
internal val COMPOSE_PREVIEW_MANAGER = DataKey.create<ComposePreviewManager>("$PREFIX.Manager")
internal val COMPOSE_PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<ComposePreviewElementInstance>("$PREFIX.PreviewElement")

@TestOnly fun getComposePreviewManagerKeyForTests() = COMPOSE_PREVIEW_MANAGER

/**
 * Returns a [ComposePreviewManager] related to the current context (which is implied to be bound to
 * a particular file), or null if one is not found. The search is done among the open preview parts
 * and `PreviewRepresentation` of the selected file editor.
 *
 * This call might access the [CommonDataKeys.VIRTUAL_FILE] so it should not be called in the EDT
 * thread. For actions using it, they should use [ActionUpdateThread.BGT].
 */
internal fun findComposePreviewManagerForContext(context: DataContext): ComposePreviewManager? {
  context.getData(COMPOSE_PREVIEW_MANAGER)?.let {
    // The context is associated to a ComposePreviewManager so return it
    return it
  }

  // Fallback to finding the ComposePreviewManager by looking into the selected editor
  val project = context.getData(CommonDataKeys.PROJECT) ?: return null
  val file = context.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null

  return FileEditorManager.getInstance(project)?.getSelectedEditor(file)?.getComposePreviewManager()
}

/**
 * Returns whether the [ComposePreviewManager] corresponding to the given [DataContext] is currently
 * refreshing.
 */
internal fun isPreviewRefreshing(context: DataContext) =
  findComposePreviewManagerForContext(context)?.status()?.isRefreshing == true

/** Returns whether the filter of preview is enabled. */
internal fun isPreviewFilterEnabled(context: DataContext): Boolean {
  return COMPOSE_PREVIEW_MANAGER.getData(context)?.isFilterEnabled ?: false
}

private fun EditorMode?.getVisibility(defaultValue: PreferredVisibility) =
  when (this) {
    EditorMode.CODE -> HIDDEN
    EditorMode.SPLIT -> SPLIT
    EditorMode.DESIGN -> FULL
    null -> defaultValue
  }

/** Returns the [ComposePreviewManager] or null if this [FileEditor] is not a Compose preview. */
fun FileEditor.getComposePreviewManager(): ComposePreviewManager? =
  when (this) {
    is MultiRepresentationPreview -> this.currentRepresentation as? ComposePreviewManager
    is TextEditorWithMultiRepresentationPreview<out MultiRepresentationPreview> ->
      this.preview.currentRepresentation as? ComposePreviewManager
    else -> null
  }
