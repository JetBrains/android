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
package com.android.tools.property.ptable2.impl

/**
 * Helper class to advance in a table cell by cell (row first).
 */
class PTablePosition(var row: Int, var column: Int, val rows: Int, val columns: Int) {

  fun next(forward: Boolean): Boolean {
    if (forward) {
      return forwards()
    }
    else {
      return backwards()
    }
  }

  private fun forwards(): Boolean {
    column++
    if (column < columns) {
      return true
    }
    return nextRow()
  }

  private fun backwards(): Boolean {
    column--
    if (column >= 0) {
      return true
    }
    return previousRow()
  }

  private fun nextRow(): Boolean {
    row++
    column = 0
    return row < rows
  }

  private fun previousRow(): Boolean {
    row--
    column = columns - 1
    return row >= 0
  }
}
