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
package com.android.tools.idea.layoutinspector.util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Various checks for tests.
 */
object CheckUtil {

  /**
   * Return the line at the [offset] of the specified [file] as a string.
   */
  fun findLineAtOffset(file: VirtualFile, offset: Int): String {
    val text = String(file.contentsToByteArray(), Charsets.UTF_8)
    val line = StringUtil.offsetToLineColumn(text, offset)
    val lineText = text.substring(offset - line.column, text.indexOf('\n', offset))
    return lineText.trim()
  }
}
