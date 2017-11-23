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

import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Font
import javax.swing.Icon

/**
 * Action that shows a dialog to change the tag name of a component
 */
class MorphComponentAction(component: NlComponent, designSurface: DesignSurface)
  : AnAction("Convert view...") {

  private val myFacet: AndroidFacet = component.model.facet
  private val mySurface = designSurface
  private val myProject = component.model.project
  private val myNlComponent = component
  private val myAttributes = mutableListOf<AttributeProperties>()
  private lateinit var myTagNameTextRange: TextRange
  private lateinit var myTagNameRange: RangeHighlighterEx
  private var myNewName = component.tagName

  init {
    templatePresentation.isEnabled = true
    templatePresentation.isVisible = true
  }

  /**
   * Build a map from [TextRange] to the corresponding sub-[String] in the returned [String] representation of
   * [nlComponent]
   *
   * [attributeProperties] will be cleared before parsing the component.
   * The first value of [attributeProperties] corresponds to the tag name.
   *
   * For now, other values are useless, but they will be used to allow the user to delete some argument before
   * morphing the component
   */
  private fun buildRangesAndGetString(nlComponent: NlComponent, attributeProperties: MutableList<AttributeProperties>): String {
    var xmlString = "<"
    val indent = "    "
    val tag = nlComponent.tag

    attributeProperties.clear()
    myTagNameTextRange = TextRange(xmlString.length, xmlString.length + nlComponent.tagName.length)
    xmlString += nlComponent.tagName

    tag.attributes.forEach { nlAttribute ->
      xmlString += "\n$indent"
      attributeProperties.add(AttributeProperties(xmlString.length, nlAttribute.name, nlAttribute.textLength))
      xmlString += nlAttribute.text
    }

    xmlString += ">\n$indent..."
    return xmlString
  }

  /**
   * Create a read-only editor displaying the content of the provided component.
   *
   * The editor highlight the content as XML
   */
  private fun createEditor(): EditorEx {
    val factory = EditorFactory.getInstance()
    val document = factory.createDocument(buildRangesAndGetString(myNlComponent, myAttributes))
    val editor = factory.createEditor(document, myProject, XmlFileType.INSTANCE, false) as EditorEx

    val syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(XmlFileType.INSTANCE, myProject, mySurface.model?.virtualFile)
    syntaxHighlighter?.let {
      editor.highlighter = LexerEditorHighlighter(it, EditorColorsManager.getInstance().globalScheme)
    }

    myTagNameRange = addMarkerForTagName(editor)

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
  private fun addMarkerForTagName(editor: EditorEx): RangeHighlighterEx {
    val start = myTagNameTextRange.startOffset
    val end = myTagNameTextRange.endOffset
    val attributes = TextAttributes(null, null, editor.colorsScheme.getColor(EditorColors.MODIFIED_LINES_COLOR), EffectType.BOXED, Font.BOLD)
    return editor.markupModel.addRangeHighlighterAndChangeAttributes(
        start, end,
        HighlighterLayer.SELECTION, attributes,
        HighlighterTargetArea.EXACT_RANGE, false,
        { highlighter: RangeHighlighterEx ->
          highlighter.isGreedyToLeft = true
          highlighter.isGreedyToRight = true
        })
  }

  /**
   * Apply the provided tag name to the component in the model
   */
  private fun applyTagEdit(newTagName: String) {
    NlWriteCommandAction.run(myNlComponent, "Convert " + myNlComponent.tagName + " to $newTagName", {
      myNlComponent.tag.name = newTagName
      TransactionGuard.getInstance().submitTransactionAndWait {
        myAttributes
            .filter { !it.keep }
            .map { it.name }
            .forEach {
              myNlComponent.tag.setAttribute(it, null)
            }
        myNlComponent.removeObsoleteAttributes()
        myNlComponent.children.forEach(NlComponent::removeObsoleteAttributes)
      }
    })
  }

  private fun createMorphPopup(morphPanel: MorphPanel, editorEx: EditorEx): JBPopup {
    val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(morphPanel, null)
        .setMovable(true)
        .setFocusable(true)
        .setRequestFocus(true)
        .setShowShadow(true)
        .setCancelOnClickOutside(true)
        .setAdText("Set the new type for the selected View")
        .setCancelCallback {
          if (!editorEx.isDisposed) {
            EditorFactory.getInstance().releaseEditor(editorEx)
          }
          true
        }
        .createPopup()

    morphPanel.setOkAction {
      applyTagEdit(myNewName)
      popup.closeOk(null)
    }
    return popup
  }

  private fun showMorphPopup() {
    val oldTagName = myNlComponent.tagName
    val morphSuggestion = MorphManager.getMorphSuggestion(myNlComponent)
    val editor = createEditor()
    val morphDialog = MorphPanel(myFacet, myProject, editor.component, oldTagName, morphSuggestion)
    morphDialog.setTagNameChangeConsumer(updateDocumentWithNewName(editor.document))
    createMorphPopup(morphDialog, editor).showInFocusCenter()
    IdeFocusManager.getInstance(myProject).requestFocus(morphDialog.preferredFocusComponent, true)
  }

  private fun updateDocumentWithNewName(document: Document): (String) -> Unit = { newName ->
    myNewName = newName
    document.replaceString(myTagNameRange.startOffset, myTagNameRange.endOffset, newName)
  }

  override fun actionPerformed(e: AnActionEvent?) = showMorphPopup()

  /**
   * Render showing a remove or add icon and running the provided [runnable] when clicking
   * on it.
   */
  private class IconRenderer(keep: Boolean, val runnable: Runnable) : GutterIconRenderer() {
    val myIcon: Icon = if (keep) AllIcons.General.Remove else AllIcons.General.Add

    override fun getClickAction() = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent?) = runnable.run()
    }

    override fun getIcon(): Icon = myIcon

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class != other::class) return false
      return myIcon != (other as IconRenderer).myIcon
    }

    override fun hashCode(): Int = myIcon.hashCode()
  }

  /**
   * Data class to store useful information about an [NlComponent]'s attribute
   */
  private class AttributeProperties(val startOffset: Int,
                                    val name: String,
                                    length: Int,
                                    var keep: Boolean = true,
                                    var highlighter: RangeHighlighter? = null) {
    val endOffset = startOffset + length

    private val myStrikeoutTextAttributes = TextAttributes(null, null,
        EditorColorsManager.getInstance().globalScheme.getColor(
            EditorColors.DELETED_LINES_COLOR),
        EffectType.STRIKEOUT,
        Font.ITALIC)

    /**
     * Remove the old [com.intellij.openapi.editor.RangeMarker] in  [editor] for this [AttributeProperties],
     * which will have its [AttributeProperties.keep] property inverted. A new [com.intellij.openapi.editor.RangeMarker]
     * will be added with the updated this [AttributeProperties]
     */
    private fun toggleKeep(editor: EditorEx) {
      keep = !keep
      removeMarker(editor)
      addMarker(editor)
    }

    private fun removeMarker(editor: EditorEx) {
      highlighter?.let {
        editor.markupModel.removeHighlighter(it)
      }
    }

    internal fun addMarker(editorEx: EditorEx) {
      highlighter = editorEx.markupModel.addRangeHighlighter(startOffset, endOffset,
          HighlighterLayer.SELECTION,
          if (!keep) myStrikeoutTextAttributes else null,
          HighlighterTargetArea.EXACT_RANGE)

      highlighter?.let {
        it.gutterIconRenderer = IconRenderer(keep, Runnable {
          toggleKeep(editorEx)
        })
      }
    }
  }
}

