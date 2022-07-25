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
package com.android.tools.property.panel.impl.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.adtui.model.stdui.PooledThreadExecution
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.impl.model.util.FakeEnumSupport
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

@RunsInEdt
class ComboBoxPropertyEditorModelTest {

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @get:Rule
  val edtRule = EdtRule()

  private fun createEnumSupport(action: AnAction? = null, delayed: Boolean = false) =
    FakeEnumSupport("visible", "invisible", "gone", action = action, delayed = delayed)

  private fun createModel(editable: Boolean = true): ComboBoxPropertyEditorModel = createModel(createEnumSupport(), editable)

  private fun createModel(enumSupport: EnumSupport, editable: Boolean = true): ComboBoxPropertyEditorModel {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible", editingSupport = MyEditingSupport())
    property.defaultValue = "defaultNone"
    return ComboBoxPropertyEditorModel(property, enumSupport, editable)
  }

  private fun createModelWithListener(enumSupport: EnumSupport): Pair<ComboBoxPropertyEditorModel, ValueChangedListener> {
    val model = createModel(enumSupport)
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return model to listener
  }

  @Test
  fun testValue() {
    val model = createModel()
    assertThat(model.value).isEqualTo("visible")
    assertThat(model.placeHolderValue).isEqualTo("defaultNone")
  }

  @Test
  fun testInitialDropDownValue() {
    val model = createModel(editable = false)
    assertThat(model.selectedItem).isEqualTo(EnumValue.item("visible"))
    assertThat(model.size).isEqualTo(2)
    assertThat(model.getElementAt(0)).isEqualTo(model.selectedItem)
    assertThat(model.getElementAt(1)).isEqualTo(EnumValue.LOADING)
  }

  @Test
  fun testInitialDropDownValueAfterPropertyUpdate() {
    val model = createModel(editable = false)
    model.property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "gone")
    assertThat(model.selectedItem).isEqualTo(EnumValue.item("gone"))
    assertThat(model.size).isEqualTo(2)
    assertThat(model.getElementAt(0)).isEqualTo(model.selectedItem)
    assertThat(model.getElementAt(1)).isEqualTo(EnumValue.LOADING)
  }

  @Test
  fun testDropDownValueWithEmptyList() {
    val model = createModel(editable = false, enumSupport = FakeEnumSupport())

    val initialItem: Any? = model.selectedItem
    assertThat(model.selectedItem).isEqualTo(EnumValue.item("visible"))
    assertThat(model.getIndexOfCurrentValue()).isEqualTo(0)

    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)

    assertThat(model.selectedItem).isSameAs(initialItem)
    assertThat(model.getIndexOfCurrentValue()).isEqualTo(-1)
  }

  @Test
  fun testDropDownIsDefaultValueWithNewEmptyValue() {
    val model = ComboBoxPropertyEditorModel(
      property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "").apply { defaultValue = "invisible" },
      enumSupport = createEnumSupport(),
      editable = false
    )

    assertThat(model.selectedItem).isEqualTo(EnumValue.item("invisible"))
    assertThat(model.getIndexOfCurrentValue()).isEqualTo(-1)

    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)

    assertThat(model.selectedItem).isEqualTo(EnumValue.item("invisible"))

    model.value = "visible"
    model.refresh()
    assertThat(model.selectedItem).isEqualTo(EnumValue.item("visible"))

    model.value = "" // Should change back to show the default value
    model.refresh()
    assertThat(model.selectedItem).isEqualTo(EnumValue.item("invisible"))
  }

  @Test
  fun testSelectedItemFromInit() {
    val model = createModel()
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)
    assertThat(model.selectedItem.toString()).isEqualTo("visible")
  }

  @Test
  fun testListModel() {
    val model = createModel()
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)

    assertThat(model.size).isEqualTo(3)
    assertThat(model.getElementAt(0).toString()).isEqualTo("visible")
    assertThat(model.getElementAt(1).toString()).isEqualTo("invisible")
    assertThat(model.getElementAt(2).toString()).isEqualTo("gone")
  }

  @Test
  fun testFocusLossWillUpdateValue() {
    // setup
    val model = createModel()
    model.focusGained()
    model.text = "#333333"

    // test
    model.focusLost()
    assertThat(model.hasFocus).isFalse()
    assertThat(model.property.value).isEqualTo("#333333")
  }

  @Test
  fun testFocusLossWithUnchangedValueWillNotUpdateValue() {
    // setup
    val (model, listener) = createModelWithListener(createEnumSupport())
    model.focusGained()

    // test
    model.focusLost()
    assertThat(model.property.value).isEqualTo("visible")
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testFocusLossWithErrorWillNotUpdateValue() {
    val (model, listener) = createModelWithListener(createEnumSupport())
    model.focusGained()
    model.text = "error"

    model.focusLost()
    assertThat(model.property.value).isEqualTo("visible")
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testEnterUpdatesValue() {
    val (model, listener) = createModelWithListener(createEnumSupport())
    model.text = "invisible"
    model.enterKeyPressed()

    assertThat(model.property.value).isEqualTo("invisible")
    verify(listener).valueChanged()
  }

  @Test
  fun testEnterWithErrorWillNotUpdateValue() {
    val (model, listener) = createModelWithListener(createEnumSupport())
    model.text = "error"
    model.enterKeyPressed()

    assertThat(model.property.value).isEqualTo("visible")
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testListenersAreConcurrentModificationSafe() {
    // Make sure that ConcurrentModificationException is NOT generated from the code below:
    val model = createModel()
    val listener = RecursiveListDataListener(model)
    model.addListDataListener(listener)
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)
    model.selectedItem = "text"
    assertThat(listener.called).isTrue()
  }

  @Test
  fun testListModelWithSlowEnumSupport() {
    val enumSupport = createEnumSupport(delayed = true)
    val model = createModel(enumSupport)
    var controlNotified = false
    val future = model.popupMenuWillBecomeVisible { controlNotified = true }

    assertThat(model.size).isEqualTo(1)
    assertThat(model.getElementAt(0)!!.display).isEqualTo("Loading...")
    assertThat(controlNotified).isFalse()

    enumSupport.releaseAll()
    future.get(2, TimeUnit.SECONDS)
    assertThat(controlNotified).isTrue()
    assertThat(model.size).isEqualTo(3)
    assertThat(model.getElementAt(0)!!.display).isEqualTo("visible")
    assertThat(model.getElementAt(1)!!.display).isEqualTo("invisible")
    assertThat(model.getElementAt(2)!!.display).isEqualTo("gone")
    assertThat(controlNotified).isTrue()
  }

  @Test
  fun testCannotSelectLoadingValue() {
    val enumSupport = createEnumSupport(delayed = true)
    val model = createModel(enumSupport)
    assertThat(model.value).isEqualTo("visible")
    model.popupMenuWillBecomeVisible {}

    val loading = model.getElementAt(0)!!
    assertThat(loading.display).isEqualTo("Loading...")
    model.selectedItem = loading
    assertThat(model.value).isEqualTo("visible")

    // cleanup
    enumSupport.releaseAll()
  }

  private class RecursiveListDataListener(private val model: ComboBoxPropertyEditorModel) : ListDataListener {
    var called = false

    override fun intervalRemoved(event: ListDataEvent) {
    }

    override fun intervalAdded(event: ListDataEvent) {
    }

    override fun contentsChanged(event: ListDataEvent) {
      model.addListDataListener(RecursiveListDataListener(model))
      called = true
    }
  }
}

private class MyEditingSupport : EditingSupport {
  override val execution: PooledThreadExecution
    get() = { ApplicationManager.getApplication().executeOnPooledThread(it) }

  override val validation: EditingValidation = { text ->
    if (text?.lowercase(Locale.US) == "error") {
      Pair(EditingErrorCategory.ERROR, "")
    }
    else {
      EDITOR_NO_ERROR
    }
  }
}

