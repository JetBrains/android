/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.editor

import com.android.SdkConstants
import com.android.annotations.concurrency.UiThread
import com.android.resources.ResourceFolderType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.common.type.XmlDesignerEditorFileType
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.QuickDefinitionProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider that accepts [XmlFile]s whose type belongs to [acceptedTypes].Subclasses are responsible
 * for specifying the types accepted, creating the editor using [createEditor], and specifying their
 * ID via [getEditorTypeId]. This parent class in turn is responsible for registering the accepted
 * types against [DesignerTypeRegistrar].
 */
abstract class DesignerEditorProvider
protected constructor(
  acceptedTypes: List<DesignerEditorFileType>,
  private val editorTypeId: String,
) : AsyncFileEditorProvider, QuickDefinitionProvider, DumbAware {
  private val acceptedXmlTypes = acceptedTypes.filterIsInstance<XmlDesignerEditorFileType>()

  init {
    acceptedTypes.forEach { type -> DesignerTypeRegistrar.register(type) }
  }

  override fun accept(project: Project, virtualFile: VirtualFile): Boolean {
    // accept must be quick so we do not load PSI objects here, we just look at the extension and
    // directory structure.
    if (virtualFile.extension != SdkConstants.EXT_XML) return false
    val parentFolder = virtualFile.parent
    val resourceFolderType = ResourceFolderType.getFolderType(parentFolder.name)

    return acceptedXmlTypes.any { type -> type.resourceFolderType == resourceFolderType }
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    throw UnsupportedOperationException()
  }

  @Suppress("UnstableApiUsage")
  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    // This is a suspendable method so we can spend the time here identifying which one of the
    // supported file types this editor is going to use.
    // If we can not detect the specific type, we return a generic TextEditor.
    val psiFile = readAction { PsiManager.getInstance(project).findFile(file)!! }
    val textEditor =
      withContext(Dispatchers.EDT) {
        TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
      }
    if (psiFile !is XmlFile) return textEditor
    val fileType = acceptedXmlTypes.find { type -> type.isResourceTypeOf(psiFile) }
    if (fileType == null) {
      thisLogger().warn("$file was accepted by the DesignerEditorProvider but is not supported")
      return textEditor
    }

    return withContext(Dispatchers.EDT) {
      val designEditor = createDesignEditor(project, file, fileType)
      val editorPanel = designEditor.getComponent()
      addCaretListener(textEditor, designEditor)
      editorPanel.setFileEditorDelegate(textEditor)
      val splitEditor = DesignToolsSplitEditor(textEditor, designEditor, project)
      editorPanel.workBench.setFileEditor(splitEditor)
      return@withContext splitEditor
    }
  }

  private fun addCaretListener(editor: TextEditor, designEditor: DesignerEditor) {
    val caretModel = editor.editor.caretModel
    val updateQueue =
      MergingUpdateQueue(
        "split.editor.preview.edit",
        NlModel.Companion.DELAY_AFTER_TYPING_MS,
        true,
        null,
        designEditor,
        null,
        Alarm.ThreadToUse.SWING_THREAD,
      )
    updateQueue.setRestartTimerOnAdd(true)
    val caretListener: CaretListener =
      object : CaretListener {
        override fun caretAdded(event: CaretEvent) {
          caretPositionChanged(event)
        }

        override fun caretPositionChanged(event: CaretEvent) {
          val surface = designEditor.getComponent().surface
          val sceneView = surface.focusedSceneView
          val offset = caretModel.offset
          if (sceneView == null || offset == -1) {
            return
          }

          val model = sceneView.sceneManager.model
          var views: ImmutableList<NlComponent> = model.treeReader.findByOffset(offset)
          if (views.isEmpty()) {
            views = model.treeReader.components
          }
          handleCaretChanged(sceneView, views)
          updateQueue.queue(
            object : Update("Design editor update") {
              override fun run() {
                surface.repaint()
              }

              override fun canEat(update: Update): Boolean {
                return true
              }
            }
          )
        }
      }
    caretModel.addCaretListener(caretListener)
    // If the editor is just opening the SceneView may not be set yet. Register a listener so we get
    // updated once we can get the model.
    designEditor
      .getComponent()
      .surface
      .addListener(
        object : DesignSurfaceListener {
          @UiThread
          override fun modelsChanged(surface: DesignSurface<*>, models: List<NlModel?>) {
            surface.removeListener(this)
            val caretModel = editor.editor.caretModel
            caretListener.caretPositionChanged(
              CaretEvent(
                caretModel.currentCaret,
                caretModel.logicalPosition,
                caretModel.logicalPosition,
              )
            )
          }
        }
      )
  }

  protected abstract fun handleCaretChanged(sceneView: SceneView, views: ImmutableList<NlComponent>)

  /**
   * Creates a new [DesignerEditor] for the given file contained in the given [project]. [fileType]
   * indicates which of the file types supported by this provider is the one that was selected.
   */
  abstract fun createDesignEditor(
    project: Project,
    file: VirtualFile,
    fileType: DesignerEditorFileType,
  ): DesignerEditor

  final override fun getEditorTypeId(): String = editorTypeId

  override fun getPolicy(): FileEditorPolicy {
    // We hide the default one since the split editor already includes the text-only view.
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }
}
