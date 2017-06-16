/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.idea.uibuilder.model.NlComponent
import com.android.tools.idea.uibuilder.surface.DesignSurface
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Font

/**
 * Action that shows a dialog to change the tag name of a component
 */
class MorphComponentAction(component: NlComponent, designSurface: DesignSurface)
  : AnAction("Morph View...") {

  private val myFacet: AndroidFacet = component.model.facet
  private val mySurface = designSurface
  private val myProject = component.model.project
  private val myComponent = component
  private val myRangesToText = mutableListOf<Pair<TextRange, String>>()
  private var myTagNameRange: RangeHighlighterEx? = null
  private var myNewName = ""

  init {
    templatePresentation.isEnabled = true
    templatePresentation.isVisible = true
  }

  /**
   * Creates a new document containing a String representation of the provided component
   * @see buildRangesAndGetString
   */
  private fun createDocument(nlComponent: NlComponent): Document {
    val factory = EditorFactory.getInstance()
    val stringRepresentation = buildRangesAndGetString(nlComponent, myRangesToText)
    return (factory as EditorFactoryImpl).createDocument(stringRepresentation, true, false)
  }

  /**
   *
   * Build a map from [TextRange] to the corresponding sub-[String] in the returned [String] representation of
   * [nlComponent]
   *
   * [rangesMap] will be cleared before parsing the component.
   * The first value of [rangesMap] corresponds to the tag name.
   *
   * For now, other values are useless, but they will be used to allow the user to delete some argument before
   * morphing the component
   */
  private fun buildRangesAndGetString(nlComponent: NlComponent, rangesMap: MutableList<Pair<TextRange, String>>): String {
    var stringRepresentation = "<"
    val indent = "    "
    val tag = nlComponent.tag

    rangesMap.clear()
    rangesMap.add(createTextRange(stringRepresentation.length, nlComponent.tagName))
    stringRepresentation += nlComponent.tagName

    tag.attributes.forEach { xmlAttribute ->
      rangesMap.add(createTextRange(stringRepresentation.length, xmlAttribute.text))
      stringRepresentation += "\n$indent" + xmlAttribute.text
    }

    stringRepresentation += ">\n$indent..."
    return stringRepresentation
  }

  /**
   * Create a new [Pair] mapping a [TextRange] to the corresponding [text].
   * The [TextRange] is set from [offset] to `offset + text.length + 1`
   */
  private fun createTextRange(offset: Int, text: String) = TextRange(offset, offset + text.length) to text

  /**
   * Create a read-only editor displaying the content of the provided component.
   *
   * The editor highlight the content as XML
   */
  private fun createEditor(document: Document): EditorEx {
    val editor = EditorFactory.getInstance().createEditor(document, myProject, XmlFileType.INSTANCE, false) as EditorEx
    val syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(XmlFileType.INSTANCE, myProject, mySurface.model?.file?.virtualFile)
    syntaxHighlighter?.let {
      editor.highlighter = LexerEditorHighlighter(it, EditorColorsManager.getInstance().globalScheme)
    }

    myTagNameRange = createTagNameMarker(editor)
    with(editor) {
      setCaretEnabled(false)
      isViewer = true
      settings.isLineNumbersShown = false
      settings.additionalLinesCount = 1
    }
    return editor
  }

  /**
   * Create the marker shown around the tag name
   */
  private fun createTagNameMarker(editor: EditorEx): RangeHighlighterEx? {
    if (myRangesToText.isEmpty()) {
      return null
    }
    val start = myRangesToText[0].first.startOffset
    val end = myRangesToText[0].first.endOffset
    val attributes = TextAttributes(null, null, editor.colorsScheme.getColor(EditorColors.MODIFIED_LINES_COLOR), EffectType.BOXED, Font.BOLD)
    return editor.markupModel.addRangeHighlighterAndChangeAttributes(
        start, end,
        HighlighterLayer.SELECTION, attributes,
        HighlighterTargetArea.EXACT_RANGE, false, {
      it.isGreedyToLeft = true
      it.isGreedyToRight = true
    })
  }

  /**
   * Apply the provided tag name to the component in the model
   */
  private fun applyNewTagName(newTagName: String) =
      TransactionGuard.getInstance().submitTransactionAndWait {
        WriteCommandAction.runWriteCommandAction(myProject, "Morph " + myComponent.tagName + " to $newTagName", null,
            Runnable {
              myComponent.tag.name = newTagName
            }, myComponent.tag.containingFile)
      }

  private fun createMorphPopup(morphDialog: MorphDialog): JBPopup {
    val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(morphDialog, null)
        .setMinSize(morphDialog.preferredSize)
        .setMovable(true)
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnClickOutside(true)
        .setAdText("Set the new type for the selected View")
        .createPopup()

    morphDialog.setOkAction {
      applyNewTagName(myNewName)
      popup.closeOk(null)
    }
    return popup
  }

  private fun showMorphPopup() {
    val document = createDocument(myComponent)
    val editor = createEditor(document)
    val oldTagName = myRangesToText[0].second
    val morphDialog = MorphDialog(myFacet, myProject, editor.component, oldTagName)

    morphDialog.setTagNameChangeConsumer(updateDocumentWithNewName(document))
    createMorphPopup(morphDialog).showInFocusCenter()
    IdeFocusManager.getInstance(myProject).requestFocus(morphDialog.preferredFocusComponent, true)
  }

  private fun updateDocumentWithNewName(document: Document): (String) -> Unit = {
    newName ->
    myNewName = newName
    myTagNameRange?.let {
      document.replaceString(it.startOffset, it.endOffset, newName)
    }
  }

  override fun actionPerformed(e: AnActionEvent?) = showMorphPopup()
}

