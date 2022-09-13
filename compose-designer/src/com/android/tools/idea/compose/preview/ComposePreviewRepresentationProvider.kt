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
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.actions.SetColorBlindModeAction
import com.android.tools.idea.actions.SetScreenViewProviderAction
import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.compose.preview.actions.ComposeNotificationGroup
import com.android.tools.idea.compose.preview.actions.GroupSwitchAction
import com.android.tools.idea.compose.preview.actions.ShowDebugBoundaries
import com.android.tools.idea.compose.preview.actions.StopAnimationInspectorAction
import com.android.tools.idea.compose.preview.actions.StopInteractivePreviewAction
import com.android.tools.idea.compose.preview.actions.visibleOnlyInComposeStaticPreview
import com.android.tools.idea.compose.preview.scene.COMPOSE_BLUEPRINT_SCREEN_VIEW_PROVIDER
import com.android.tools.idea.compose.preview.scene.COMPOSE_SCREEN_VIEW_PROVIDER
import com.android.tools.idea.compose.preview.util.ComposeAdapterLightVirtualFile
import com.android.tools.idea.compose.preview.util.ComposePreviewElement
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.representation.CommonRepresentationEditorFileType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.uibuilder.actions.LayoutManagerSwitcher
import com.android.tools.idea.uibuilder.actions.SwitchSurfaceLayoutManagerAction
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiFile
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.uipreview.AndroidEditorSettings

/** [ToolbarActionGroups] that includes the actions that can be applied to Compose Previews. */
private class ComposePreviewToolbar(private val surface: DesignSurface<*>) :
  ToolbarActionGroups(surface) {

  override fun getNorthGroup(): ActionGroup =
    DefaultActionGroup(
      listOfNotNull(
        StopInteractivePreviewAction(),
        StopAnimationInspectorAction(),
        GroupSwitchAction().visibleOnlyInComposeStaticPreview(),
        SwitchSurfaceLayoutManagerAction(
            layoutManagerSwitcher = surface.sceneViewLayoutManager as LayoutManagerSwitcher,
            layoutManagers = PREVIEW_LAYOUT_MANAGER_OPTIONS
          ) { !isAnyPreviewRefreshing(it.dataContext) }
          .visibleOnlyInComposeStaticPreview(),
        StudioFlags.COMPOSE_DEBUG_BOUNDS.ifEnabled { ShowDebugBoundaries() },
        if (surface is NlDesignSurface)
          ViewModesDropDownAction(surface).visibleOnlyInComposeStaticPreview()
        else null
      )
    )

  override fun getNorthEastGroup(): ActionGroup = ComposeNotificationGroup(surface)

  /** [DropDownAction] to toggle through the available viewing modes for the Compose preview. */
  private class ViewModesDropDownAction(private val surface: NlDesignSurface) :
    DropDownAction(
      message("action.scene.mode.title"),
      message("action.scene.mode.description"),
      // TODO(b/160021437): Modify tittle/description to avoid using internal terms: 'Design
      // Surface'
      StudioIcons.LayoutEditor.Toolbar.VIEW_MODE
    ) {
    private val disabledIcon =
      IconLoader.getDisabledIcon(StudioIcons.LayoutEditor.Toolbar.VIEW_MODE)

    init {
      templatePresentation.isHideGroupIfEmpty = true
      val blueprintEnabled = StudioFlags.COMPOSE_BLUEPRINT_MODE.get()
      val colorBlindEnabled = StudioFlags.COMPOSE_COLORBLIND_MODE.get()
      if (blueprintEnabled || colorBlindEnabled) {
        addAction(SetScreenViewProviderAction(COMPOSE_SCREEN_VIEW_PROVIDER, surface))
      }
      if (blueprintEnabled) {
        addAction(SetScreenViewProviderAction(COMPOSE_BLUEPRINT_SCREEN_VIEW_PROVIDER, surface))
      }
      if (colorBlindEnabled) {
        addAction(
          DefaultActionGroup.createPopupGroup {
            message("action.scene.mode.colorblind.dropdown.title")
          }
            .apply {
              addAction(SetColorBlindModeAction(ColorBlindMode.PROTANOPES, surface))
              addAction(SetColorBlindModeAction(ColorBlindMode.PROTANOMALY, surface))
              addAction(SetColorBlindModeAction(ColorBlindMode.DEUTERANOPES, surface))
              addAction(SetColorBlindModeAction(ColorBlindMode.DEUTERANOMALY, surface))
              addAction(SetColorBlindModeAction(ColorBlindMode.TRITANOPES, surface))
            }
        )
      }
    }

    override fun createCustomComponent(presentation: Presentation, place: String) =
      ActionButtonWithToolTipDescription(this, presentation, place).apply {
        border = JBUI.Borders.empty(1, 2)
      }

    override fun update(e: AnActionEvent) {
      super.update(e)
      val shouldEnableAction = !isAnyPreviewRefreshing(e.dataContext)
      e.presentation.isEnabled = shouldEnableAction
      // Since this is an ActionGroup, IntelliJ will set the button icon to enabled even though it
      // is disabled. Only when clicking on the
      // button the icon will be disabled (and gets re-enabled when releasing the mouse), since the
      // action itself is disabled and not popup
      // will show up. Since we want users to know immediately that this action is disabled, we
      // explicitly set the icon style when the
      // action is disabled.
      e.presentation.icon =
        if (shouldEnableAction) StudioIcons.LayoutEditor.Toolbar.VIEW_MODE else disabledIcon
    }
  }
}

/** A [PreviewRepresentationProvider] coupled with [ComposePreviewRepresentation]. */
class ComposePreviewRepresentationProvider(
  private val filePreviewElementProvider: () -> FilePreviewElementFinder =
    ::defaultFilePreviewElementFinder
) : PreviewRepresentationProvider {
  private val LOG = Logger.getInstance(ComposePreviewRepresentationProvider::class.java)

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
   * [PreviewRepresentation] of them.
   */
  override suspend fun accept(project: Project, psiFile: PsiFile): Boolean =
    psiFile.virtualFile.isKotlinFileType() && (psiFile.getModuleSystem()?.usesCompose ?: false)

  /** Creates a [ComposePreviewRepresentation] for the input [psiFile]. */
  override fun createRepresentation(psiFile: PsiFile): ComposePreviewRepresentation {
    val previewProvider =
      object : PreviewElementProvider<ComposePreviewElement> {
        override suspend fun previewElements(): Sequence<ComposePreviewElement> =
          filePreviewElementProvider()
            .findPreviewMethods(psiFile.project, psiFile.virtualFile)
            .asSequence()
      }
    val hasPreviewMethods =
      filePreviewElementProvider().hasPreviewMethods(psiFile.project, psiFile.virtualFile)
    if (LOG.isDebugEnabled) {
      LOG.debug("${psiFile.virtualFile.path} hasPreviewMethods=${hasPreviewMethods}")
    }

    val isComposableEditor =
      hasPreviewMethods ||
        filePreviewElementProvider().hasComposableMethods(psiFile.project, psiFile.virtualFile)
    val globalState = AndroidEditorSettings.getInstance().globalState

    return ComposePreviewRepresentation(
      psiFile,
      previewProvider,
      if (isComposableEditor) globalState.preferredComposableEditorVisibility()
      else globalState.preferredKotlinEditorVisibility(),
      ::ComposePreviewViewImpl
    )
  }

  override val displayName = message("representation.name")
}

private const val PREFIX = "ComposePreview"
internal val COMPOSE_PREVIEW_MANAGER = DataKey.create<ComposePreviewManager>("$PREFIX.Manager")
internal val COMPOSE_PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<ComposePreviewElementInstance>("$PREFIX.PreviewElement")

/**
 * Returns a list of all [ComposePreviewManager]s related to the current context (which is implied
 * to be bound to a particular file). The search is done among the open preview parts and
 * [PreviewRepresentation]s (if any) of open file editors.
 */
internal fun findComposePreviewManagersForContext(
  context: DataContext
): List<ComposePreviewManager> {
  context.getData(COMPOSE_PREVIEW_MANAGER)?.let {
    // The context is associated to a ComposePreviewManager so return it
    return listOf(it)
  }

  // Fallback to finding the ComposePreviewManager by looking into all the editors
  val project = context.getData(CommonDataKeys.PROJECT) ?: return emptyList()
  val file = context.getData(CommonDataKeys.VIRTUAL_FILE) ?: return emptyList()

  return FileEditorManager.getInstance(project)?.getEditors(file)?.mapNotNull {
    it.getComposePreviewManager()
  }
    ?: emptyList()
}

/** Returns whether any preview manager is currently refreshing. */
internal fun isAnyPreviewRefreshing(context: DataContext) =
  findComposePreviewManagersForContext(context).any { it.status().isRefreshing }

// We will default to split mode if there are @Preview annotations in the file or if the file
// contains @Composable.
private fun AndroidEditorSettings.GlobalState.preferredComposableEditorVisibility() =
  when (preferredComposableEditorMode) {
    AndroidEditorSettings.EditorMode.CODE -> PreferredVisibility.HIDDEN
    AndroidEditorSettings.EditorMode.SPLIT -> PreferredVisibility.SPLIT
    AndroidEditorSettings.EditorMode.DESIGN -> PreferredVisibility.FULL
    else -> PreferredVisibility.SPLIT // default
  }

// We will default to code mode for kotlin files not containing any @Composable functions.
private fun AndroidEditorSettings.GlobalState.preferredKotlinEditorVisibility() =
  when (preferredKotlinEditorMode) {
    AndroidEditorSettings.EditorMode.CODE -> PreferredVisibility.HIDDEN
    AndroidEditorSettings.EditorMode.SPLIT -> PreferredVisibility.SPLIT
    AndroidEditorSettings.EditorMode.DESIGN -> PreferredVisibility.FULL
    else -> PreferredVisibility.HIDDEN // default
  }

/** Returns the [ComposePreviewManager] or null if this [FileEditor] is not a Compose preview. */
fun FileEditor.getComposePreviewManager(): ComposePreviewManager? =
  when (this) {
    is MultiRepresentationPreview -> this.currentRepresentation as? ComposePreviewManager
    is TextEditorWithMultiRepresentationPreview<out MultiRepresentationPreview> ->
      this.preview.currentRepresentation as? ComposePreviewManager
    else -> null
  }
