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
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.PooledThreadExecution
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.impl.model.util.FakeAction
import com.android.tools.property.panel.impl.model.util.FakeEnumSupport
import com.android.tools.property.panel.impl.model.util.FakeInspectorLineModel
import com.android.tools.property.panel.impl.model.util.FakeLineType
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.testing.PropertyAppRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

@RunsInEdt
class ComboBoxPropertyEditorModelTest {

  @get:Rule
  val appRule = PropertyAppRule()

  @get:Rule
  val edtRule = EdtRule()

  private fun createEnumSupport(action: AnAction? = null, delayed: Boolean = false) =
    FakeEnumSupport("visible", "invisible", "gone", action = action, delayed = delayed)

  private fun createModel(): ComboBoxPropertyEditorModel = createModel(createEnumSupport())

  private fun createModel(enumSupport: EnumSupport): ComboBoxPropertyEditorModel {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible", editingSupport = MyEditingSupport())
    property.defaultValue = "defaultNone"
    return ComboBoxPropertyEditorModel(property, enumSupport, true)
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
  fun testSelectedItemFromInit() {
    val model = createModel()
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)
    assertThat(model.selectedItem.toString()).isEqualTo("visible")
  }

  @Test
  fun testSelectItemIsKeptAfterFocusLoss() {
    val model = createModel()
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)
    model.selectedItem = "gone"

    model.popupMenuWillBecomeInvisible(false)
    model.focusLost()

    assertThat(model.property.value).isEqualTo("gone")
  }

  @Test
  fun testSelectActionItemShouldNotUpdateValueOnFocusLoss() {
    val model = createModel()
    var future: Future<*>? = null
    val action = object : AnAction() {
      override fun actionPerformed(event: AnActionEvent) {
        model.focusLost()
        future = ApplicationManager.getApplication().executeOnPooledThread { model.property.value = "gone" }
      }
    }
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)
    model.selectedItem = EnumValue.action(action)
    model.popupMenuWillBecomeInvisible(false)
    assertThat(model.property.value).isEqualTo("visible")

    // The action is executed delayed on the UI event queue:
    while (future == null) {
      UIUtil.dispatchAllInvocationEvents()
    }

    // Emulate a dialog is writing to the property long after the menu has been closed:
    future!!.get(2, TimeUnit.SECONDS)

    assertThat(model.property.value).isEqualTo("gone")
  }

  @Test
  fun testSelectedItemSetOnlyOnce() {
    val model = createModel()
    val property = model.property as FakePropertyItem
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)
    model.selectedItem = EnumValue.item("gone")
    model.popupMenuWillBecomeInvisible(false)

    // Emulate: propertiesGenerated event causing the current editor to refresh.
    // The underlying xml may not have updated yet.
    property.emulateLateValueUpdate("emulated")

    // This focus loss should NOT update the value again.
    model.focusLost()

    // The property value will eventually update, however in this test it will remain the emulated value:
    assertThat(model.property.value).isEqualTo("emulated")

    // Test that the property value should be updated only once:
    assertThat((model.property as FakePropertyItem).updateCount).isEqualTo(1)
  }

  @Test
  fun testEnter() {
    val (model, listener) = createModelWithListener(createEnumSupport())
    val line = FakeInspectorLineModel(FakeLineType.PROPERTY)
    model.lineModel = line
    model.text = "gone"
    model.enterKeyPressed()
    assertThat(model.property.value).isEqualTo("gone")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
  }

  @Test
  fun testEscape() {
    // setup
    val model = createModel()
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)

    model.selectedItem = "gone"
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.escapeKeyPressed()
    assertThat(model.property.value).isEqualTo("visible")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
  }

  @Test
  fun testEnterInPopup() {
    // setup
    val model = createModel()
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)

    model.isPopupVisible = true
    model.selectedItem = "gone"
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.popupMenuWillBecomeInvisible(false)
    assertThat(model.property.value).isEqualTo("gone")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
  }

  @RunsInEdt
  @Test
  fun testEnterInPopupOnAction() {
    // setup
    val action = FakeAction("testAction")
    val enumSupport = FakeEnumSupport("visible", "invisible", action = action)
    val model = createModel(enumSupport)
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)
    model.selectedItem = enumSupport.values.last()
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.popupMenuWillBecomeInvisible(false)
    assertThat(model.isPopupVisible).isFalse()
    assertThat(action.actionPerformedCount).isEqualTo(0)
    assertThat(model.property.value).isEqualTo("visible")
    verify(listener).valueChanged()

    // The action is executed delayed on the UI event queue:
    UIUtil.dispatchAllInvocationEvents()
    assertThat(action.actionPerformedCount).isEqualTo(1)
    assertThat(model.property.value).isEqualTo("visible")
    verify(listener).valueChanged()
  }

  @Test
  fun testEscapeInPopup() {
    // setup
    val model = createModel()
    model.popupMenuWillBecomeVisible {}.get(2, TimeUnit.SECONDS)
    model.selectedItem = "gone"
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.popupMenuWillBecomeInvisible(true)
    assertThat(model.property.value).isEqualTo("visible")
    assertThat(model.isPopupVisible).isFalse()
    verifyZeroInteractions(listener)
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

  private class RecursiveListDataListener(private val model: ComboBoxPropertyEditorModel): ListDataListener {
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
}

