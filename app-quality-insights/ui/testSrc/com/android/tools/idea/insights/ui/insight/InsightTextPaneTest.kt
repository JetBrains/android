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
package com.android.tools.idea.insights.ui.insight

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.ApplicationRule
import java.awt.datatransfer.DataFlavor
import org.junit.Rule
import org.junit.Test

class InsightTextPaneTest {

  @get:Rule val applicationRule = ApplicationRule()

  private val markdownText =
    """
    The application crashed with a `java.lang.RuntimeException` exception with the message "Test Crash vcs". This could be caused by a number of factors, such as:

    * **NullPointerException:** The code might be attempting to access a null object, which would result in a `NullPointerException`.
    * **ArrayIndexOutOfBoundsException:** The code might be trying to access an element in an array that is out of bounds, which would result in an `ArrayIndexOutOfBoundsException`.
  """
      .trimIndent()
  private val renderedText =
    """
    The application crashed with a java.lang.RuntimeException exception with the message "Test Crash vcs". This could be caused by a number of factors, such as:
    NullPointerException:  The code might be attempting to access a null object, which would result in a NullPointerException.
    ArrayIndexOutOfBoundsException:  The code might be trying to access an element in an array that is out of bounds, which would result in an ArrayIndexOutOfBoundsException.
  """
      .trimIndent()

  @Test
  fun `copy text with selection`() {
    val pane = InsightTextPane()
    pane.text = markdownText

    pane.select(149, 178)
    pane.performCopy(DataContext.EMPTY_CONTEXT)
    assertThat(CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor))
      .isEqualTo("such as:\nNullPointerException")
  }

  @Test
  fun `copy entire text`() {
    val pane = InsightTextPane()
    pane.text = markdownText
    pane.performCopy(DataContext.EMPTY_CONTEXT)
    assertThat(CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor))
      .isEqualTo(renderedText)
  }
}
