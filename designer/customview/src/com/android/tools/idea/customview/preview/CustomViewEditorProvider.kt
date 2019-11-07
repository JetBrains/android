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
package com.android.tools.idea.customview.preview

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.SourceCodeChangeListener
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.compose.preview.ComposeFileEditorProvider
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.hasClassesExtending
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import icons.StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW
import icons.StudioIcons.LayoutEditor.Toolbar.WRAP_HEIGHT
import icons.StudioIcons.LayoutEditor.Toolbar.WRAP_WIDTH
import org.jetbrains.kotlin.idea.KotlinFileType

internal const val CUSTOM_VIEW_PREVIEW_ID = "android-custom-view"

private val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

internal class CustomViewLightVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  override fun getParent() = FAKE_LAYOUT_RES_DIR
}

internal fun VirtualFile.hasSourceFileExtension() = when (extension) {
  KotlinFileType.INSTANCE.defaultExtension, JavaFileType.INSTANCE.defaultExtension -> true
  else -> false
}

fun PsiClass.extendsView(): Boolean {
  return this.qualifiedName == "android.view.View" || this.extendsListTypes.any {
    it.resolve()?.extendsView() ?: false
  }
}

private val SUPPORTED_WIDGETS_FQCN = setOf(
  "android.view.ConstraintLayout",
  "android.view.LinearLayout",
  "android.view.RelativeLayout",
  "android.view.View",
  "android.view.ViewGroup",
  "android.widget.Button",
  "android.widget.CheckBox",
  "android.widget.CompoundButton",
  "android.widget.EditText",
  "android.widget.ImageView",
  "android.widget.TextView",
  "androidx.appcompat.widget.AppCompatButton",
  "androidx.appcompat.widget.AppCompatCheckBox",
  "androidx.appcompat.widget.AppCompatEditText",
  "androidx.appcompat.widget.AppCompatImageView",
  "androidx.appcompat.widget.AppCompatTextView"
)

private fun PsiFile.hasClassesExtendingWidget() = node.hasClassesExtending(SUPPORTED_WIDGETS_FQCN)

internal fun PsiFile.hasViewSuccessor() =
  when (this) {
    is PsiClassOwner -> {
      if (DumbService.isDumb(this.project)) {
        this.hasClassesExtendingWidget() // Using heuristic when in Dumb mode
      }
      else {
        this.classes.any { it.extendsView() } // Properly detect inheritance from View in Smart mode
      }
    }
    else -> false
  }

/**
 * Text editor that enables preview if the file contains classes extending android.view.View
 */
internal class TextEditorWithCustomViewPreview(textEditor: TextEditor, preview: CustomViewPreview) : SeamlessTextEditorWithPreview<CustomViewPreview>(
  textEditor, preview, "Custom View and Preview") {
  init {
    preview.editorWithPreview = this
    isPureTextEditor = preview.currentState.isEmpty()
  }
}

/**
 * This provider creates a special text editor extended with a preview for the files containing custom views. Namely, Java and Kotlin
 * source files containing classes that extend (explicitly or implicitly through  a chain of other classes) an android.view.View class
 * for which we can get a visual representation.
 */
class CustomViewEditorProvider : FileEditorProvider, DumbAware {
  private object CustomViewEditorFileType : DesignerEditorFileType {
    override fun isResourceTypeOf(file: PsiFile) = file.virtualFile is CustomViewLightVirtualFile

    override fun getToolbarActionGroups(surface: DesignSurface) = CustomViewPreviewToolbar(surface)

    override fun getSelectionContextToolbar(surface: DesignSurface, selection: List<NlComponent>): DefaultActionGroup =
      DefaultActionGroup()

    override fun isEditable() = true
  }

  init {
    DesignerTypeRegistrar.register(CustomViewEditorFileType)
  }

  // TODO(b/143067434): remove ComposeFileEditorProvider check and rework it so that Compose and custom View previews work together
  override fun accept(project: Project, file: VirtualFile) =
    StudioFlags.NELE_CUSTOM_VIEW_PREVIEW.get() && !ComposeFileEditorProvider().accept(project, file) && file.hasSourceFileExtension()

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    val previewRepresentation = CustomViewPreviewRepresentation(psiFile)
    val designEditor = CustomViewPreview(psiFile, previewRepresentation)
    val editorWithPreview = TextEditorWithCustomViewPreview(textEditor, designEditor)

    // Queue to avoid refreshing notifications on every key stroke
    val modificationQueue = MergingUpdateQueue("Notifications Update queue",
                                               100,
                                               true,
                                               null,
                                               editorWithPreview)
      .apply {
        setRestartTimerOnAdd(true)
      }

    val updateNotifications = object : Update("updateNotifications") {
      override fun run() {
        if (editorWithPreview.isModified) {
          EditorNotifications.getInstance(project).updateNotifications(file)
        }
      }
    }

    PsiManager.getInstance(project).addPsiTreeChangeListener(SourceCodeChangeListener(psiFile) {
      modificationQueue.queue(updateNotifications)
    }, editorWithPreview)

    return editorWithPreview
  }

  override fun getEditorTypeId() = CUSTOM_VIEW_PREVIEW_ID

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

internal fun FileEditor.getCustomViewPreviewManager(): CustomViewPreviewManager? = when(this) {
  is CustomViewPreview -> this
  is MultiRepresentationPreview -> this.currentRepresentation as? CustomViewPreviewManager
  else -> null
}
/**
 * Toolbar that creates a dropdown button allowing to select between the custom views available from the current file and two toggle buttons
 * for switching vertical and horizontal wrap content options.
 * TODO(b/143067434): Utilize this for switching between custom view and compose representations
 */
private class CustomViewPreviewToolbar(private val surface: DesignSurface) :
  ToolbarActionGroups(surface) {

  private fun findPreviewEditors(): List<CustomViewPreviewManager> = FileEditorManager.getInstance(surface.project)?.let { fileEditorManager ->
    surface.models.flatMap { fileEditorManager.getAllEditors(it.virtualFile).asIterable() }
      .filterIsInstance<SeamlessTextEditorWithPreview<out FileEditor>>()
      .mapNotNull { it.preview.getCustomViewPreviewManager() }
      .distinct()
  } ?: listOf()

  override fun getNorthGroup(): ActionGroup {
    val customViewPreviewActions = DefaultActionGroup()
    val customViews = object : DropDownAction(null, "Custom View for Preview", CUSTOM_VIEW) {
      override fun update(e: AnActionEvent) {
        super.update(e)
        removeAll()

        // We need just a single previewEditor here (any) to retrieve (read) the states and currently selected state
        findPreviewEditors().firstOrNull()?.let { previewEditor ->
          previewEditor.states.forEach {
            val state = it
            add(object : AnAction(it) {
              override fun actionPerformed(e: AnActionEvent) {
                // Here we iterate over all editors as change in selection (write) should trigger updates in all of them
                findPreviewEditors().forEach { it.currentState = state }
              }
            })
          }
          e.presentation.setText(previewEditor.currentState, false)
        }
      }

      override fun displayTextInToolbar() = true
    }

    val wrapWidth = object : ToggleAction(null, "Set preview width to wrap content", WRAP_WIDTH) {
      override fun isSelected(e: AnActionEvent) = findPreviewEditors().any { it.shrinkWidth }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        findPreviewEditors().forEach { it.shrinkWidth = state }
      }
    }

    val wrapHeight = object : ToggleAction(null, "Set preview height to wrap content", WRAP_HEIGHT) {
      override fun isSelected(e: AnActionEvent) = findPreviewEditors().any { it.shrinkHeight }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        findPreviewEditors().forEach { it.shrinkHeight = state }
      }
    }

    customViewPreviewActions.add(customViews)
    customViewPreviewActions.add(wrapWidth)
    customViewPreviewActions.add(wrapHeight)

    return customViewPreviewActions
  }
}

class OutdatedCustomViewNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  private val COMPONENT_KEY = Key.create<EditorNotificationPanel>("com.android.tools.idea.uibuilder.editor.custom.outdated")

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (fileEditor !is TextEditorWithCustomViewPreview) return null

    // Do not show the notification while the build is in progress
    if (GradleBuildState.getInstance(project).isBuildInProgress) return null

    val isModified = FileDocumentManager.getInstance().isFileModified(file)
    if (!isModified) {
      // The file was saved, check the compilation time
      val modificationStamp = file.timeStamp
      val lastBuildTimestamp = PostProjectBuildTasksExecutor.getInstance(project).lastBuildTimestamp ?: -1
      if (lastBuildTimestamp >= modificationStamp) return null
    }

    val module = ModuleUtil.findModuleForFile(file, project) ?: return null

    val psiFile = PsiManager.getInstance(project).findFile(file)!!

    val hasCustomView = psiFile.hasViewSuccessor()

    // If they are different -- nothing has changed, we do not need to display anything
    if (hasCustomView != fileEditor.isPureTextEditor) return null

    val message = when {
      hasCustomView and fileEditor.isPureTextEditor -> "You have custom view classes in the source code. Preview will appear after a successful build."
      else -> "You no longer have custom view classes in the source code. Preview will disappear after a successful build."
    }

    return EditorNotificationPanel().apply {
      setText(message)
      createActionLabel("Refresh", Runnable {
        GradleBuildInvoker.getInstance(project).compileJava(arrayOf(module), TestCompileType.NONE)
      })
    }
  }

  override fun getKey(): Key<EditorNotificationPanel> = COMPONENT_KEY
}
