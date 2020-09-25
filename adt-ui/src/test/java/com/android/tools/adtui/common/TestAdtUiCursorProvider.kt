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
package com.android.tools.adtui.common

import java.awt.Cursor

/**
 * The custom cursors cannot be created in headless environment thus we cannot test them by bazel.
 * For testing purpose, replace the [AdtUiCursorType] with built-in cursors to make them testable.
 * Be aware that the replacing cursors must not be used during the testing, otherwise the test result may be false positive.
 *
 * By default, the resizing cursors are replaced with the built-in ones.
 */
class TestAdtUiCursorsProvider : AdtUiCursorsProvider {
  private val replacedCursorMap = mutableMapOf<AdtUiCursorType, Cursor>()

  init {
    replacedCursorMap[AdtUiCursorType.SW_RESIZE] = Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR)
    replacedCursorMap[AdtUiCursorType.SE_RESIZE] = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
    replacedCursorMap[AdtUiCursorType.NW_RESIZE] = Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
    replacedCursorMap[AdtUiCursorType.NE_RESIZE] = Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
    replacedCursorMap[AdtUiCursorType.N_RESIZE] = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
    replacedCursorMap[AdtUiCursorType.S_RESIZE] = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
    replacedCursorMap[AdtUiCursorType.W_RESIZE] = Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
    replacedCursorMap[AdtUiCursorType.E_RESIZE] = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
  }

  override fun getCursor(type: AdtUiCursorType): Cursor = replacedCursorMap.getOrDefault(type, Cursor.getDefaultCursor())

  /**
   * Replace the given [AdtUiCursorType] type to a predefined [Cursor] for testing in headless environment.
   */
  internal fun replaceCursorForTest(type: AdtUiCursorType, cursor: Cursor) {
    replacedCursorMap[type] = cursor
  }
}
