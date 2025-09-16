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
package com.android.tools.idea.layoutinspector.stateinspection

import com.android.adblib.utils.createChildScope
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN
import com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal val STATE_READ_EDITOR_KEY = Key.create<Editor>("StateReadEditor")
const val RECOMPOSITION_TEXT_LABEL_NAME = "RecompositionTextLabel"
const val STATE_READ_TEXT_LABEL_NAME = "StateReadTextLabel"

/** Convenience function for creating a StateInspectionPanel */
internal fun createStateInspectionPanel(
  layoutInspector: LayoutInspector,
  parentDisposable: Disposable,
): StateInspectionPanel {
  val inspectorModel = layoutInspector.inspectorModel
  val project = inspectorModel.project
  val model =
    StateInspectionModelImpl(
      inspectorModel,
      layoutInspector.coroutineScope,
      layoutInspector.treeSettings,
      { layoutInspector.currentClient },
      parentDisposable,
    )
  val uiScope = parentDisposable.createCoroutineScope(extraContext = Dispatchers.EDT)
  return StateInspectionPanel(model, project, uiScope, parentDisposable)
}

/** A panel to display state reads for recompositions. */
internal class StateInspectionPanel(
  model: StateInspectionModel,
  project: Project,
  scope: CoroutineScope,
  parentDisposable: Disposable,
) : AdtSecondaryPanel(BorderLayout()) {
  private var innerPanel: InnerStateInspectionPanel? = null
    set(value) {
      field?.let {
        Disposer.dispose(it)
        removeAll()
      }
      field = value
      value?.let { add(it, BorderLayout.CENTER) }
    }

  init {
    isVisible = false
    scope.launch {
      model.show.collect { show ->
        isVisible = show
        innerPanel =
          if (!show) null
          else
            InnerStateInspectionPanel(
              this@StateInspectionPanel,
              model,
              project,
              scope,
              parentDisposable,
            )
      }
    }
  }
}

/**
 * This inner panel is not created before we need to show state reads, and it is destroyed when the
 * state read panel is hidden. In this way the (heavy) editor is not created unless it is needed.
 */
private class InnerStateInspectionPanel(
  private val parent: StateInspectionPanel,
  model: StateInspectionModel,
  project: Project,
  parentScope: CoroutineScope,
  parentDisposable: Disposable,
) : AdtSecondaryPanel(BorderLayout()), Disposable {
  private val scope = parentScope.createChildScope()
  private val title =
    object : JBLabel() {
      override fun updateUI() {
        super.updateUI()
        font = JBUI.Fonts.smallFont()
      }
    }
  private val recompositionText = JBLabel().apply { name = RECOMPOSITION_TEXT_LABEL_NAME }
  private val stateReadCountText = JBLabel().apply { name = STATE_READ_TEXT_LABEL_NAME }
  private val editor = createStateReadEditor(project, this)
  private val hyperlinkDetector = StateInspectionHyperLinkDetector(project, editor, scope, this)
  private val foldingDetector = StateInspectionFoldingDetector(editor, scope)

  init {
    Disposer.register(parentDisposable, this)
    parent.putUserData(STATE_READ_EDITOR_KEY, editor) // For testing
    title.text = LayoutInspectorBundle.message("layout.inspector.recomposition.state.reads")
    title.border = JBUI.Borders.empty(2, 5)
    val prev = ActionButton(model.prevAction, null, UNKNOWN, DEFAULT_MINIMUM_BUTTON_SIZE)
    prev.maximumSize = DEFAULT_MINIMUM_BUTTON_SIZE
    val next = ActionButton(model.nextAction, null, UNKNOWN, DEFAULT_MINIMUM_BUTTON_SIZE)
    next.maximumSize = DEFAULT_MINIMUM_BUTTON_SIZE
    val minimize = ActionButton(model.minimizeAction, null, UNKNOWN, DEFAULT_MINIMUM_BUTTON_SIZE)
    minimize.maximumSize = DEFAULT_MINIMUM_BUTTON_SIZE
    minimize.border = JBUI.Borders.emptyRight(10)

    // Header with title, scroller through the recompositions, a state read count, minimize button
    val header = JPanel()
    header.layout = BoxLayout(header, BoxLayout.X_AXIS)
    header.add(title)
    header.add(Box.createHorizontalGlue())
    header.add(prev)
    header.add(recompositionText)
    header.add(next)
    header.add(Box.createHorizontalStrut(JBUIScale.scale(20)))
    header.add(stateReadCountText)
    header.add(Box.createHorizontalGlue())
    header.add(minimize)
    header.border = JBUI.Borders.customLineBottom(JBColor.border())

    add(header, BorderLayout.NORTH)
    add(editor.component, BorderLayout.CENTER)
    border = JBUI.Borders.empty()

    scope.launch { model.recompositionText.collect { recompositionText.text = it } }
    scope.launch { model.stateReadsText.collect { stateReadCountText.text = it } }
    scope.launch { model.stackTraceText.collect { setTextInEditor(it) } }
    scope.launch { model.updates.collect { updateButtons(prev, next, minimize) } }
  }

  override fun dispose() {
    scope.cancel()
    parent.putUserData(STATE_READ_EDITOR_KEY, null)
  }

  private fun updateButtons(vararg buttons: ActionButton) {
    buttons.forEach { it.update() }
  }

  private suspend fun setTextInEditor(text: String) {
    val document = editor.document
    try {
      document.setReadOnly(false)
      edtWriteAction { document.setText(text) }
      hyperlinkDetector.detectHyperlinks()
      foldingDetector.detectFolding()
    } finally {
      document.setReadOnly(true)
    }
  }

  private fun createStateReadEditor(project: Project, disposable: Disposable): EditorEx {
    val editorFactory = EditorFactory.getInstance()
    val document = editorFactory.createDocument("")
    (document as DocumentImpl).setAcceptSlashR(true)
    UndoUtil.disableUndoFor(document)
    val editor = editorFactory.createViewer(document, project, EditorKind.CONSOLE) as EditorEx
    val editorSettings = editor.settings
    editorSettings.isAllowSingleLogicalLineFolding = true
    editorSettings.isLineMarkerAreaShown = false
    editorSettings.isIndentGuidesShown = false
    editorSettings.isLineNumbersShown = false
    editorSettings.isFoldingOutlineShown = true
    editorSettings.isAdditionalPageAtBottom = false
    editorSettings.additionalColumnsCount = 0
    editorSettings.additionalLinesCount = 0
    editorSettings.isRightMarginShown = false
    editorSettings.isCaretRowShown = false
    editorSettings.isShowingSpecialChars = false
    editor.gutterComponentEx.isPaintBackground = false
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.scrollPane.verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
    Disposer.register(disposable) { editorFactory.releaseEditor(editor) }
    return editor
  }

  private fun <T> Component?.putUserData(key: Key<T>, data: T?) {
    (this as? JComponent)?.putClientProperty(key, data)
  }
}
