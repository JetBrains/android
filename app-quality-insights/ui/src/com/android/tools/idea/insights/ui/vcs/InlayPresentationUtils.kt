/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui.vcs

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.LineCenteredInset
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.WithCursorOnHoverPresentation
import com.intellij.openapi.editor.Editor
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.EnumSet

internal fun InlayPresentation.withLineCentered(editor: Editor): InlayPresentation {
  return LineCenteredInset(this, editor)
}

internal fun InlayPresentation.withOnClick(
  factory: PresentationFactory,
  onClick: (MouseEvent, Point) -> Unit
): InlayPresentation {
  return factory.onClick(
    base = this,
    buttons = EnumSet.of(MouseButton.Left, MouseButton.Middle),
    onClick = onClick
  )
}

internal fun InlayPresentation.withHandCursor(editor: Editor): InlayPresentation {
  return WithCursorOnHoverPresentation(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), editor)
}
