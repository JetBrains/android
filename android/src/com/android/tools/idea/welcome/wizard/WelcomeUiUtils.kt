/*
 * Copyright (C) 2014 The Android Open Source Project
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

/**
 * Functions to style controls for a consistent user UI
 */
@file:JvmName("WelcomeUiUtils")

package com.android.tools.idea.welcome.wizard

import com.android.sdklib.devices.Storage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.components.JBLabel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import java.awt.Component
import java.awt.Dimension
import java.math.RoundingMode
import java.text.NumberFormat
import javax.swing.JPanel

/**
 * Returns string describing the [size].
 */
fun getSizeLabel(size: Long): String {
  val unit = Storage.Unit.values().last { it.numberOfBytes <= size.coerceAtLeast(1) }

  val space = size * 1.0 / unit.numberOfBytes
  val formatted = roundToNumberOfDigits(space, 3)
  return "$formatted ${unit.displayValue}"
}

/**
 * Returns a string that rounds the number so number of integer places + decimal places is less or equal to [maxDigits].
 *
 * Number will not be truncated if it has more integer digits then [maxDigits].
 */
private fun roundToNumberOfDigits(number: Double, maxDigits: Int): String {
  var multiplier = 1
  var digits = maxDigits
  while (digits > 0 && number > multiplier) {
    multiplier *= 10
    digits--
  }
  return NumberFormat.getNumberInstance().apply {
    isGroupingUsed = false
    roundingMode = RoundingMode.HALF_UP
    maximumFractionDigits = digits
  }.format(number)
}

/**
 * Appends [details] to the [message] if they are not empty.
 */
fun getMessageWithDetails(message: String, details: String?): String =
  if (details.isNullOrBlank()) {
    "$message."
  }
  else {
    val dotIfNeeded = if (details.trim().endsWith(".")) "" else "."
    "$message: $details$dotIfNeeded"
  }

internal fun invokeLater(modalityState: ModalityState = ModalityState.defaultModalityState(), block: () -> Unit): Unit =
  ApplicationManager.getApplication().invokeLater(block, modalityState)

// TODO(qumeric): rename, refactor, add more capabilities
// * do not let to add two things in the same cell
// * add support for button groups?
// * add support for default spacing with spacers?
// * dynamic number of rows
// * multicolumn/multirow support
// * add JavaDoc
// * move to different file
class VerticalPanel(row: Int, col: Int, isHorizontal: Boolean = false, private val block: VerticalPanel.() -> Unit) {
  private val panel = JPanel(GridLayoutManager(row, col))
  private val any = Dimension(-1, -1)
  private var curRow = 0
  private var curCol = 0
  private val rowInc = if (isHorizontal) 0 else 1
  private val colInc = if (isHorizontal) 1 else 0

  fun elem(c: Component, anchor: Int = 0, fill: Int = 0, hPolicy: Int = 0, vPolicy: Int = 0) {
    panel.add(c, GridConstraints(curRow, curCol, 1, 1, anchor, fill, hPolicy, vPolicy, any, any, any))
    curRow += rowInc
    curCol += colInc
  }

  fun label(text: String, anchor: Int = 0, fill: Int = 0, hPolicy: Int = 0, vPolicy: Int = 0) =
    elem(JBLabel(text), anchor, fill, hPolicy, vPolicy)

  fun html(text: String, anchor: Int = 0, fill: Int = 0, hPolicy: Int = 0, vPolicy: Int = 0) =
    label("<html>$text</html>", anchor, fill, hPolicy, vPolicy)

  // TODO(qumeric): should it be hspacer/vspacer instead?
  fun spacer(anchor: Int = 0, fill: Int = 0, hPolicy: Int = 0, vPolicy: Int = 0) =
    elem(Spacer(), anchor, fill, hPolicy, vPolicy)

  fun build(): JPanel {
    block()
    return panel
  }
}
