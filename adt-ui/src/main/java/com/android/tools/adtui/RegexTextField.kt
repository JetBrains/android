/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.LightColors
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.TestOnly
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.regex.PatternSyntaxException
import javax.swing.Icon
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.Element
import javax.swing.text.PlainDocument

/**
 * A text field with history, clear & regex buttons.
 *
 * This class is based on FilterComponent RegexFilterComponent & SearchTextField. The JTextField client properties added to
 * ExtendableTextField are handled by TextFieldWithPopupHandlerUI.
 */
@UiThread
class RegexTextField @TestOnly internal constructor(
  private val historyPropertyName: String,
  private val historySize: Int,
  delayUpdateMs: Long,
  private val propertiesComponent: PropertiesComponent = PropertiesComponent.getInstance(),
  coroutineScope: CoroutineScope,
) : BorderLayoutPanel() {

  /**
   * Public constructor.
   *
   * @param disposableParent A [Disposable] parent
   * @param historyPropertyName The name under which the history for this field will be saved
   * @param historySize Max number of history items to save
   * @param delayUpdateMs If greater than 0, updates to [OnChangeListener] will be debounced by this many milliseconds
   * @param propertiesComponent A [PropertiesComponent] to save/load history. Defaults to application level but can be provided
   */
  constructor(
    disposableParent: Disposable,
    historyPropertyName: String,
    historySize: Int = 5,
    delayUpdateMs: Long = 0L,
    propertiesComponent: PropertiesComponent = PropertiesComponent.getInstance(),
  ) : this(
    historyPropertyName,
    historySize,
    delayUpdateMs,
    propertiesComponent,
    AndroidCoroutineScope(disposableParent, uiThread))

  var isRegex: Boolean = false
  var text: String
    get() = textField.text
    set(value) {
      textField.text = value
    }

  private val textField = ExtendableTextField()
  private val listeners = mutableListOf<OnChangeListener>()

  init {
    assert(historyPropertyName.isNotBlank())
    assert(historySize > 0)
    assert(delayUpdateMs >= 0)

    addToCenter(textField)

    textField.apply {
      addExtension(HistoryExtension())
      addExtension(RegexExtension())
      addExtension(ClearExtension())
      putClientProperty("JTextField.Search.Gap", JBUIScale.scale(6))
      putClientProperty("JTextField.variant", "extendable")
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_ENTER) {
            e.consume()
            addToHistory(text)
            onFilterChange()
          }
        }
      })
    }

    if (delayUpdateMs > 0) {
      @Suppress("EXPERIMENTAL_API_USAGE")
      callbackFlow {
        textField.document.addDocumentListener(object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            if (e is RegexToggleEvent) {
              // Do not debounce regex toggle event.
              onFilterChange()
            }
            else {
              trySend(Unit)
            }
          }
        })
        awaitClose()
      }.debounce(delayUpdateMs).onEach { onFilterChange() }.launchIn(coroutineScope)
    }
    else {
      textField.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          onFilterChange()
        }
      })
    }

    DumbAwareAction.create { showPopup() }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("ShowSearchHistory"), this)
  }

  fun addOnChangeListener(listener: OnChangeListener) {
    listeners.add(listener)
  }

  @UiThread
  private fun onFilterChange() {
    if (isRegex && !isValidRegex(text)) {
      textField.background = LightColors.RED
      return
    }
    textField.background = UIUtil.getTextFieldBackground()
    for (listener in listeners) {
      listener.onChange(this)
    }
  }

  @UiThread
  private fun showPopup() {
    addToHistory(textField.text)
    JBPopupFactory.getInstance().createPopupChooserBuilder(propertiesComponent.getValues(historyPropertyName)?.asList() ?: emptyList())
      .setMovable(false)
      .setRequestFocus(true)
      .setItemChosenCallback { textField.text = it }
      .createPopup()
      .showUnderneathOf(this)
  }

  private fun addToHistory(element: String) {
    if (element.isEmpty()) {
      return
    }
    val history = propertiesComponent.getValues(historyPropertyName)?.asList()?.toMutableList() ?: mutableListOf()
    history.remove(element)
    history.add(0, element)
    if (history.size > historySize) {
      history.removeLast()
    }
    propertiesComponent.setValues(historyPropertyName, history.toTypedArray())
  }

  private inner class HistoryExtension : ExtendableTextComponent.Extension {
    override fun getIcon(hovered: Boolean): Icon = AllIcons.Actions.SearchWithHistory

    override fun getAfterIconOffset(): Int = JBUIScale.scale(6)

    override fun getIconGap(): Int = JBUIScale.scale(2)

    override fun isIconBeforeText(): Boolean = true

    override fun getActionOnClick() = Runnable(this@RegexTextField::showPopup)
  }

  private inner class RegexExtension : ExtendableTextComponent.Extension {
    override fun getIcon(hovered: Boolean) =
      if (isRegex) AllIcons.Actions.RegexSelected
      else AllIcons.Actions.Regex

    override fun getActionOnClick() = Runnable {
      isRegex = !isRegex
      val plainDocument = textField.document as PlainDocument
      // Trigger a document change so that TextFieldWithPopupHandlerUI will update the icon.
      plainDocument.getListeners(DocumentListener::class.java).forEach {
        it.changedUpdate(RegexToggleEvent(plainDocument))
      }
    }
  }

  private inner class ClearExtension : ExtendableTextComponent.Extension {
    override fun getIcon(hovered: Boolean): Icon? {
      return when {
        textField.text.isEmpty() -> null
        hovered -> AllIcons.Actions.CloseHovered
        else -> AllIcons.Actions.Close
      }
    }

    override fun getActionOnClick() = Runnable {
      textField.text = ""
    }
  }

  /**
   * A listener that is invoked whenever the [text] or the value of [isRegex] changes.
   *
   * Might be delayed by up to delayUpdateMs milliseconds.
   */
  interface OnChangeListener {
    @UiThread
    fun onChange(component: RegexTextField)
  }
}

private fun isValidRegex(text: String): Boolean {
  return try {
    Regex(text)
    true
  }
  catch (e: PatternSyntaxException) {
    false
  }
}

/**
 * A specialized DocumentEvent that signals to the handler that we changed toggled the regex. This is so that the regex toggles do not
 * debounce.
 */
private class RegexToggleEvent(private val document: Document) : DocumentEvent {
  override fun getOffset() = 0
  override fun getLength() = 0
  override fun getDocument() = document
  override fun getType(): DocumentEvent.EventType = DocumentEvent.EventType.CHANGE
  override fun getChange(elem: Element?) = null
}