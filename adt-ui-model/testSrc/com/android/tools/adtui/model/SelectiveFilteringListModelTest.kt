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
package com.android.tools.adtui.model

import com.android.tools.adtui.model.stdui.SelectiveFilteringListModel
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.codeStyle.NameUtil
import org.junit.Test
import javax.swing.DefaultListModel

class SelectiveFilteringListModelTest {

  @Test
  fun testBasics() {
    assertThat(pullElementsFromFilter("tools:text")).containsExactly("tools:text", "tools:autoText", "tools:textAlignment").inOrder()
  }

  private fun pullElementsFromFilter(text: String): List<String> {
    val model = DefaultListModel<String>()
    model.addElement("textAlignment")
    model.addElement("tools:autoText")
    model.addElement("tools:text")
    model.addElement("tools:textAlignment")

    val matcher = NameUtil.buildMatcher("*$text").build()
    val condition = { element: String -> matcher.matches(element) }
    val selective = SelectiveFilteringListModel(model)
    selective.perfectMatch = text
    selective.setFilter(condition)

    val result = mutableListOf<String>()
    for (i in 0 until selective.size) {
      result.add(selective.getElementAt(i))
    }
    return result
  }
}