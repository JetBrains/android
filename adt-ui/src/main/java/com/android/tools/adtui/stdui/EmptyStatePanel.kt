/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.instructions.IconInstruction
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.RenderInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.instructions.UrlInstruction
import com.intellij.icons.AllIcons
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtilities
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

private val TEXT_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(13f)

class UrlData(val text: String, val url: String)

sealed class Chunk
class IconChunk(val icon: Icon) : Chunk()
class TextChunk(val text: String) : Chunk()
class LabelData(vararg val chunks: Chunk)

/**
 * An opinionated panel that makes it easy to generate UI that conforms to
 * https://jetbrains.github.io/ui/principles/empty_state/
 *
 * @param helpUrlData If present, shows a link at the bottom of the empty state text, offering
 * users a change to click on a link that takes them to a browser page where they can read more
 * about what is causing the empty state / what they can do.
 */
class EmptyStatePanel @JvmOverloads constructor(private val reason: LabelData, helpUrlData: UrlData? = null): JPanel(BorderLayout()) {
  init {
    add(createInstructionsPanel(this, reason, helpUrlData))
  }

  @JvmOverloads
  constructor(reason: String, helpUrlData: UrlData? = null): this(LabelData(TextChunk(reason)), helpUrlData)

  /**
   * The raw reason text rendered by this empty state panel (in other words, icons not included)
   *
   * Useful for testing.
   */
  @get:TestOnly
  val reasonText get() = reason.chunks.mapNotNull { it as? TextChunk }.joinToString(" ") { it.text }
}

private fun createInstructionsPanel(parent: JComponent, reason: LabelData, helpUrlData: UrlData?): InstructionsPanel {
  val instructions = mutableListOf<RenderInstruction>()
  val textMetrics = UIUtilities.getFontMetrics(parent, TEXT_FONT)
  reason.chunks.forEach {
    when (it) {
      is IconChunk -> instructions.add(IconInstruction(it.icon, 5, null))
      is TextChunk -> instructions.add(TextInstruction(textMetrics, it.text))
    }
  }

  if (helpUrlData != null) {
    instructions.add(NewRowInstruction(12))
    instructions.add(IconInstruction(AllIcons.General.ContextHelp, 5, null))
    instructions.add(UrlInstruction(textMetrics.font, helpUrlData.text, helpUrlData.url))
  }

  return InstructionsPanel.Builder(*instructions.toTypedArray())
    .setMode(InstructionsPanel.Mode.FILL_PANEL)
    .setColors(UIUtil.getInactiveTextColor(), null)
    .build()
}

