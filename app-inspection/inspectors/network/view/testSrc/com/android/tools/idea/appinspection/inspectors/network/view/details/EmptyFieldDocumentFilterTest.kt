/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.google.common.truth.Truth.assertThat
import com.intellij.ui.components.JBTextField
import javax.swing.text.AbstractDocument
import org.junit.Before
import org.junit.Test

class EmptyFieldDocumentFilterTest {

  private lateinit var textField: JBTextField
  private lateinit var emptyFieldDocumentFilter: EmptyFieldDocumentFilter
  private var updateCounter = 0

  /** Increment [updateCounter] to denote the method was called */
  private fun fakeUpdateAction() {
    updateCounter++
  }

  @Before
  fun setUp() {
    updateCounter = 0
    emptyFieldDocumentFilter = EmptyFieldDocumentFilter { fakeUpdateAction() }
    textField = JBTextField()
    (textField.document as AbstractDocument).documentFilter = emptyFieldDocumentFilter
  }

  @Test
  fun testReplaceAndRemove() {
    textField.text = "A"

    // [fakeUpdateAction] should increment counter
    assertThat(updateCounter).isEqualTo(1)

    // Remove the inserted "A"
    textField.document.remove(0, 1)

    // [fakeUpdateAction] should increment counter
    assertThat(updateCounter).isEqualTo(2)
  }

  @Test
  fun testEmptyReplace() {
    textField.text = ""

    // [fakeUpdateAction] should not increment counter since the document is empty
    assertThat(updateCounter).isEqualTo(0)
  }

  @Test
  fun testInsertString() {
    textField.document.insertString(0, "header", null)

    // [fakeUpdateAction] should increment counter
    assertThat(updateCounter).isEqualTo(1)
  }

  @Test
  fun testInsertEmptyString() {
    textField.document.insertString(0, "", null)

    // [fakeUpdateAction] should not increment counter since the document is empty
    assertThat(updateCounter).isEqualTo(0)
  }
}
