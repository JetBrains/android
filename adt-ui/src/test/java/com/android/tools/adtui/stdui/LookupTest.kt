/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.adtui.model.stdui.DefaultCommonTextFieldModel
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditorCompletion
import com.android.tools.adtui.model.stdui.PooledThreadExecution
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.ListModel

class LookupTest {

  @Test
  fun testPopupBelowEditor() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    ui.editorLocation = Point(5, 50)
    field.text = "a8"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isTrue()
    assertThat(ui.location.x).isEqualTo(9)
    assertThat(ui.location.y).isEqualTo(66)
    assertThat(ui.elements()).containsExactly("a8", "@string/app_name8", "@string/app_name18", "@string/app_name28")
  }

  @Test
  fun testPopupAboveEditor() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    ui.editorLocation = Point(5, 480)
    field.text = "a7"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isTrue()
    assertThat(ui.location.y).isEqualTo(444)
    assertThat(ui.elements()).containsExactly("a7", "@string/app_name7", "@string/app_name17", "@string/app_name27")
  }

  @Test
  fun testKeepBelowAfterListIsReducedInSize() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    ui.editorLocation = Point(5, 454)

    // First make the popup show below:
    field.text = "a17"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isTrue()
    assertThat(ui.location.y).isEqualTo(470)

    // Then expand the match count such that it will show above:
    field.text = "a7"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isTrue()
    assertThat(ui.location.y).isEqualTo(418)

    // Decreasing the match count should NOT make the popup jump down below:
    field.text = "a17"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isTrue()
    assertThat(ui.location.y).isEqualTo(438)
  }

  @Test
  fun testEditorInRightSizeMakesPopupFlushWithRightSideOfScreen() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    ui.editorLocation = Point(950, 50)

    // First make the popup show below:
    field.text = "a17"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isTrue()
    assertThat(ui.location.x).isEqualTo(800)
    assertThat(ui.location.y).isEqualTo(66)
  }

  @Test
  fun testClosePopupWhenNoMatches() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a8"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isTrue()
    assertThat(ui.elements()).containsExactly("a8", "@string/app_name8", "@string/app_name18", "@string/app_name28")

    field.text = "a8z"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isFalse()
    assertThat(ui.elements()).containsExactly("a8z")
  }

  @Test
  fun testExactMatchFirst() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "@string/app_name"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isTrue()
    assertThat(ui.elements().subList(0, 3)).containsExactly("@string/app_name", "@string/app_firstName", "@string/app_name1").inOrder()
  }

  @Test
  fun testNext() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a8"
    lookup.showLookup(field.text)
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.elements()).hasSize(4)
    assertThat(ui.semiFocused).isFalse()
    for (index in 1 until 4) {
      lookup.selectNext()
      assertThat(ui.selectedIndex).isEqualTo(index)
      assertThat(ui.semiFocused).isTrue()
    }
    lookup.selectNext()
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.semiFocused).isTrue()
  }

  @Test
  fun testPrevious() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a8"
    lookup.showLookup(field.text)
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.elements()).hasSize(4)
    assertThat(ui.semiFocused).isFalse()
    for (index in 3 downTo 0) {
      lookup.selectPrevious()
      assertThat(ui.selectedIndex).isEqualTo(index)
      assertThat(ui.semiFocused).isTrue()
    }
    lookup.selectPrevious()
    assertThat(ui.selectedIndex).isEqualTo(3)
    assertThat(ui.semiFocused).isTrue()
  }

  @Test
  fun testNextPage() {
    val model = TestCommonTextFieldModel("")
    val size = model.editingSupport.completion(model.text).size + 1
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a"
    lookup.showLookup(field.text)
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.elements()).hasSize(size)
    assertThat(ui.semiFocused).isFalse()

    lookup.selectNextPage()
    assertThat(ui.selectedIndex).isEqualTo(11)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectNextPage()
    assertThat(ui.selectedIndex).isEqualTo(22)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectNextPage()
    assertThat(ui.selectedIndex).isEqualTo(33)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectNextPage()
    assertThat(ui.selectedIndex).isEqualTo(size - 1)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectNextPage()
    assertThat(ui.selectedIndex).isEqualTo(size - 1)
    assertThat(ui.semiFocused).isTrue()
  }

  @Test
  fun testPreviousPage() {
    val model = TestCommonTextFieldModel("")
    val size = model.editingSupport.completion(model.text).size + 1
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a"
    lookup.showLookup(field.text)
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.elements()).hasSize(size)
    assertThat(ui.semiFocused).isFalse()
    lookup.selectPreviousPage()
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.semiFocused).isTrue()

    lookup.selectPrevious()
    assertThat(ui.selectedIndex).isEqualTo(size - 1)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectPreviousPage()
    assertThat(ui.selectedIndex).isEqualTo(size - 12)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectPreviousPage()
    assertThat(ui.selectedIndex).isEqualTo(size - 23)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectPreviousPage()
    assertThat(ui.selectedIndex).isEqualTo(size - 34)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectPreviousPage()
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectPreviousPage()
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.semiFocused).isTrue()
  }

  @Test
  fun testSelectLast() {
    val model = TestCommonTextFieldModel("")
    val size = model.editingSupport.completion(model.text).size + 1
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a"
    lookup.showLookup(field.text)
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.elements()).hasSize(size)
    assertThat(ui.semiFocused).isFalse()
    lookup.selectLast()
    assertThat(ui.selectedIndex).isEqualTo(size - 1)
    assertThat(ui.semiFocused).isTrue()
  }

  @Test
  fun testSelectFirst() {
    val model = TestCommonTextFieldModel("")
    val size = model.editingSupport.completion(model.text).size + 1
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a"
    lookup.showLookup(field.text)
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.elements()).hasSize(size)
    assertThat(ui.semiFocused).isFalse()
    lookup.selectFirst()
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectPrevious()
    lookup.selectPrevious()
    lookup.selectPrevious()
    assertThat(ui.selectedIndex).isEqualTo(size - 3)
    assertThat(ui.semiFocused).isTrue()
    lookup.selectFirst()
    assertThat(ui.selectedIndex).isEqualTo(0)
    assertThat(ui.semiFocused).isTrue()
  }

  @Test
  fun testRestoreSelectionWithRefiningMatchPattern() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a"
    lookup.showLookup(field.text)
    lookup.selectNext()
    lookup.selectNext()
    lookup.selectNext()
    assertThat(ui.selectedIndex).isEqualTo(3)
    assertThat(ui.selectedValue).isEqualTo("@string/app_name")

    field.text = "ap"
    lookup.showLookup(field.text)
    assertThat(ui.selectedIndex).isEqualTo(2)
    assertThat(ui.selectedValue).isEqualTo("@string/app_name")
  }

  @Test
  fun testSelectWithMouseClick() {
    val model = TestCommonTextFieldModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a"
    lookup.showLookup(field.text)
    lookup.selectNext()
    lookup.selectNext()
    lookup.selectNext()
    ui.clickOnSelected()
    assertThat(field.text).isEqualTo("@string/app_name")
  }

  @Test
  fun testPopupIsShownWhenDataIsAvailable() {
    val model = TestAsyncModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "al"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isFalse()

    model.executeCompletion()
    assertThat(ui.visible).isTrue()
    assertThat(ui.elements()).containsExactly("al", "@string/almond")
  }

  @Test
  fun testPopupIsNotShownIfCancelledBeforeDataIsAvailable() {
    val model = TestAsyncModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "al"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isFalse()

    lookup.escape()
    model.executeCompletion()
    assertThat(ui.visible).isFalse()
  }

  @Test
  fun testPopupIsNotShownIfClosedBeforeDataIsAvailable() {
    val model = TestAsyncModel("")
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "al"
    lookup.showLookup(field.text)
    assertThat(ui.visible).isFalse()

    lookup.close()
    model.executeCompletion()
    assertThat(ui.visible).isFalse()
  }

  @Test
  fun testPopupDotNotAllowCustomValues() {
    val model = TestCommonTextFieldModel("")
    model.editingSupport.allowCustomValues = false
    val field = CommonTextField(model)
    val ui = TestUI()
    val lookup = Lookup(field, ui)
    field.text = "a7"
    lookup.showLookup(field.text)
    assertThat(ui.elements()).containsExactly("@string/app_name7", "@string/app_name17", "@string/app_name27")
    field.text = "a17"
    lookup.showLookup(field.text)
    assertThat(ui.elements()).containsExactly("@string/app_name17")
    assertThat(ui.visible).isTrue()
  }

  class TestUI : LookupUI {
    private var listModel: ListModel<String>? = null
    override var visible = false
    override var visibleRowCount = 0
    override var selectedIndex = -1
    override var semiFocused = false
    override val popupSize
      get() = Dimension(200, visibleRowCount * 10)
    override var selectedValue: String?
      get() = computeSelectedValue()
      set(value) {
        selectedIndex = computeSelectedIndex(value)
      }
    var location = Point()
    var editorLocation = Point()

    override var clickAction: () -> Unit = {}

    override fun createList(listModel: ListModel<String>, matcher: Matcher, editor: JComponent) {
      this.listModel = listModel
    }

    override fun updateLocation(location: Point, editor: JComponent) {
      this.location.x = location.x
      this.location.y = location.y
    }

    override fun screenBounds(editor: JComponent): Rectangle {
      return Rectangle(0, 20, 1000, 480)
    }

    override fun editorBounds(editor: JComponent): Rectangle {
      return Rectangle(editorLocation.x, editorLocation.y, 160, 20)
    }

    fun clickOnSelected() {
      clickAction()
    }

    fun elements(): List<String> {
      val list = mutableListOf<String>()
      for (index in 0 until listModel!!.size) {
        list.add(listModel!!.getElementAt(index))
      }
      return list
    }

    private fun computeSelectedValue(): String? {
      val model = listModel ?: return null
      if (selectedIndex < 0 || selectedIndex >= model.size) {
        return null
      }
      return model.getElementAt(selectedIndex)
    }

    private fun computeSelectedIndex(value: String?): Int {
      return elements().indexOf(value)
    }
  }

  class TestAsyncModel(initialValue: String) : DefaultCommonTextFieldModel(initialValue) {
    override val editingSupport: TestAsyncEditingSupport = TestAsyncEditingSupport()

    fun executeCompletion() {
      editingSupport.runnable?.run()
      editingSupport.runnable = null
    }
  }

  class TestAsyncEditingSupport : EditingSupport {
    var runnable: Runnable? = null

    override val completion: EditorCompletion = {
      listOf("@string/almond",
             "@string/app_name",
             "@string/appelsin",
             "@string/apricot",
             "@android:string/paste_as_plain_text",
             "@android:string/hello")
    }

    override val execution: PooledThreadExecution
      get() = { runnable: Runnable -> this.runnable = runnable; Futures.immediateFuture(null) }
  }
}
