/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions.ml.utils

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.Balloon.Position
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.codeFloatingToolbar.CodeFloatingToolbar
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.getBestBalloonPosition
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_G
import java.awt.event.KeyEvent.VK_UP
import java.util.function.Predicate
import javax.swing.Icon
import javax.swing.JLabel
import kotlin.math.abs

private const val MAX_COMMENT_LENGTH = 45 // Just a value that looks good right now

/** A 32x32 [Icon] that renders as blank. */
private val BLANK_ICON =
  object : Icon {
    private val size = JBUI.size(32)

    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {}

    override fun getIconWidth() = size.width()

    override fun getIconHeight() = size.height()
  }

/** Returns the offset for the start of the line containing the given [offset]. */
private fun Editor.startOfLine(offset: Int): Int {
  return softWrapModel.getSoftWrap(offset)?.start
    ?: document.getLineStartOffset(document.getLineNumber(offset))
}

/**
 * Returns the offset for the end of the line or the next time the text wraps (whichever comes
 * first) after the given [offset].
 */
private fun Editor.endOfLineOrNextWrap(offset: Int): Int {
  val documentLine = document.getLineNumber(offset)
  val lineEndOffset = document.getLineEndOffset(documentLine)
  for (softWrap in softWrapModel.getSoftWrapsForRange(offset, lineEndOffset)) {
    // If this is the next one after where we are, grab the end point of the previous line.
    if (softWrap.start > offset) {
      return softWrap.start - 1
    }
  }
  return document.getLineEndOffset(document.getLineNumber(offset))
}

/**
 * For `this` [Editor], find the upper and lower offsets at which to position the balloon. If there
 * is no selected text, it will just be where the caret is. If the selected text is a single line,
 * it will be the middle of that selected text. If the selected text spans multiple lines, it will
 * be the middle of the top line of selected text and the middle of the bottom line of selected
 * text.
 */
private fun Editor.computeUpperAndLowerTargets(): Pair<Int, Int> {
  val startOffset = caretModel.primaryCaret.selectionStart
  val endOffset =
    caretModel.primaryCaret.selectionEnd.let {
      // If there is any selected text and the end offset is the start of a line, back
      // up one and use the end of the line before it.
      if (it != startOffset && offsetToVisualPosition(it).column == 0) it - 1 else it
    }
  val startVisual = offsetToVisualPosition(startOffset)
  val endVisual = offsetToVisualPosition(endOffset)
  if (startVisual.line == endVisual.line) {
    // All on one line, easy mode.
    val top = (startOffset + endOffset) / 2
    return top to top
  } else {
    // Multiple lines, go halfway to edge of screen.
    val startOfText = startOfLine(endOffset)
    val endOfText = endOfLineOrNextWrap(startOffset)
    val top = (endOfText + startOffset) / 2
    val bottom = (startOfText + endOffset) / 2
    return top to bottom
  }
}

/**
 * Returns the vertical distance from the center of `this` [Rectangle] to a [Point].
 *
 * While this isn't exactly the vertical distance, it serves the same function in the context of a
 * comparison.
 */
private fun Rectangle.verticalDistanceTo(p: Point): Int = abs(p.y - (y + height / 2))

/**
 * From `this` [Rectangle], compare two points' distance according to [verticalDistanceTo]. If both
 * points are inside `this` [Rectangle], they are considered equidistant.
 */
private fun Rectangle.compareByDistance(p1: Point, p2: Point): Int =
  when {
    contains(p1) && contains(p2) -> 0
    else -> verticalDistanceTo(p1).compareTo(verticalDistanceTo(p2))
  }

/**
 * [JBPopupListener] that re-enables the [CodeFloatingToolbar] when the popup it is listening to is
 * closed.
 */
private object CodeFloatingToolbarEnabler : JBPopupListener {
  override fun onClosed(event: LightweightWindowEvent) {
    CodeFloatingToolbar.temporarilyDisable(false)
  }
}

/**
 * A [Balloon] that can be popped up in an editor and will locate in a reasonable place. Can host
 * multiple different actions to be taken when text is input. Dismisses itself when the user clicks
 * outside or hits escape (or Ctrl+G).
 */
class ContextualEditorBalloon
private constructor(
  private val configs: List<Config>,
  private val comment: String,
  private val showCallout: Boolean,
) {
  init {
    require(configs.isNotEmpty()) { "Must supply at least one config." }
  }

  /**
   * Keeps track of which [Config] is currently displayed. When this changes, the display is also
   * updated.
   */
  private var currentConfig = 0
    get() = synchronized(this) { field }
    set(newValue) {
      val bounded = newValue.mod(configs.size)
      synchronized(this) {
        if (bounded == field) return
        field = bounded
        configs[field].let { config: Config ->
          textField.component.emptyText.setText(config.placeholderText)
          iconPanel.component.icon = config.icon
        }
      }
    }

  private val panel: DialogPanel = createPanel()
  private val balloon =
    JBPopupFactory.getInstance()
      .createBalloonBuilder(panel)
      .setRequestFocus(true)
      .setShadow(true)
      .setShowCallout(showCallout)
      .setFillColor(JBUI.CurrentTheme.List.BACKGROUND)
      .setBorderColor(JBColor.BLUE.darker())
      .setBlockClicksThroughBalloon(true)
      .setLayer(Balloon.Layer.top)
      .createBalloon()
      .apply { setAnimationEnabled(false) }

  private lateinit var textField: Cell<JBTextField>
  private lateinit var iconPanel: Cell<JLabel>

  private var text: String = ""

  fun nextConfig() {
    currentConfig += 1
  }

  fun previousConfig() {
    currentConfig -= 1
  }

  /** Show the balloon using information about the [Editor] stored in the given [DataContext]. */
  fun show(dataContext: DataContext) {
    if (!CodeFloatingToolbar.isTemporarilyDisabled()) {
      CodeFloatingToolbar.temporarilyDisable(true)
      balloon.addListener(CodeFloatingToolbarEnabler)
    }
    CommonDataKeys.EDITOR.getData(dataContext)?.let(CodeFloatingToolbar::getToolbar)?.scheduleHide()
    computePositionAndShowBalloon(dataContext)
  }

  /**
   * Show the balloon at the given [RelativePoint], and either above or below, according to the
   * given [Position].
   */
  fun show(target: RelativePoint, preferredPosition: Position) {
    balloon.show(target, preferredPosition)
  }

  /** Hide the balloon. */
  fun hide() {
    balloon.hide()
  }

  private fun createPanel(): DialogPanel =
    panel {
        row {
          val config = configs[0]
          iconPanel = icon(config.icon)
          textField =
            textField().columns(COLUMNS_LARGE).focused().bindText(::text).apply {
              with(component) {
                emptyText.setText(config.placeholderText)
                putClientProperty(
                  TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
                  Predicate<JBTextField> { it.text.isEmpty() },
                )
                addKeyListener(ContextualBalloonKeyAdapter())
              }
              comment(this@ContextualEditorBalloon.comment, maxLineLength = MAX_COMMENT_LENGTH)
            }
        }
      }
      .forwardingFocusTo(textField.component)

  /**
   * Invoked when the user inputs text into the field. Invokes the handler of the current [Config].
   */
  private fun handle(s: String) {
    synchronized(this) { configs[currentConfig].handler.invoke(s) }
  }

  /** Forwards focus from `this` [Component] to a target [Component]. */
  private fun <T : Component> T.forwardingFocusTo(target: Component) = apply {
    addFocusListener(
      object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          target.requestFocus()
        }
      }
    )
  }

  private fun computePositionAndShowBalloon(dataContext: DataContext) {
    val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    val editor = CommonDataKeys.EDITOR.getData(dataContext)
    if (editor == null || editor.contentComponent != component) {
      balloon.show(getBestBalloonPosition(dataContext), Position.below)
      return
    }
    val (upper, lower) = editor.computeUpperAndLowerTargets()
    val upperPoint = editor.offsetToXY(upper).apply { translate(0, balloon.preferredSize.height) }
    val lowerPoint =
      editor.offsetToXY(lower).apply {
        translate(0, balloon.preferredSize.height + editor.lineHeight)
      }
    // By default, show it below.
    if (editor.scrollingModel.visibleArea.compareByDistance(lowerPoint, upperPoint) <= 0) {
      editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(lower), ScrollType.MAKE_VISIBLE)
      editor.scrollingModel.runActionOnScrollingFinished {
        // Recompute because the screen may have moved.
        val lowerPointAfterScroll =
          editor.offsetToXY(lower).apply { translate(0, editor.lineHeight) }
        balloon.show(RelativePoint(component, lowerPointAfterScroll), Position.below)
      }
    }
    editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(upper), ScrollType.MAKE_VISIBLE)
    editor.scrollingModel.runActionOnScrollingFinished {
      // Recompute because the screen may have moved.
      val upperPointAfterScroll = editor.offsetToXY(upper)
      balloon.show(RelativePoint(component, upperPointAfterScroll), Position.above)
    }
  }

  /** A configuration for an action supported by the balloon. */
  private data class Config(
    val name: String,
    val icon: Icon,
    val placeholderText: String,
    val handler: (String) -> Unit,
  )

  private inner class ContextualBalloonKeyAdapter : KeyAdapter() {
    override fun keyPressed(e: KeyEvent?) {
      when (e?.keyCode) {
        VK_ENTER -> {
          panel.apply()
          text.takeIf(String::isNotEmpty)?.let(::handle)
          hide()
        }
        VK_G -> if (e.isControlDown) hide()
        VK_UP -> previousConfig()
        VK_DOWN -> nextConfig()
      }
    }
  }

  /** DSL builder for the balloon. */
  class BuilderScope(private val comment: String, private val showCallout: Boolean) {
    private val configs: MutableList<Config> = mutableListOf()

    fun register(
      name: String,
      icon: Icon = BLANK_ICON,
      placeholderText: String = "",
      handler: (String) -> Unit,
    ) {
      configs.add(Config(name, icon, placeholderText, handler))
    }

    fun build() = ContextualEditorBalloon(configs, comment, showCallout)
  }

  companion object {
    fun contextualEditorBalloon(
      comment: String = "",
      showCallout: Boolean = true,
      content: BuilderScope.() -> Unit,
    ) = BuilderScope(comment, showCallout).apply(content).build()
  }
}
