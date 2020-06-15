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
import com.intellij.openapi.actionSystem.ActionManager
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
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ComboBoxPropertyEditorModelTest {

  @JvmField @Rule
  val appRule = PropertyAppRule()

  @JvmField @Rule
  val edtRule = EdtRule()

  private fun createModel(): ComboBoxPropertyEditorModel {
    return createModel(FakeEnumSupport("visible", "invisible", "gone"))
  }

  private fun createModel(enumSupport: EnumSupport): ComboBoxPropertyEditorModel {
    val property = FakePropertyItem(ANDROID_URI, ATTR_VISIBILITY, "visible")
    property.defaultValue = "defaultNone"
    return ComboBoxPropertyEditorModel(property, enumSupport, true)
  }

  private fun createModelWithListener(): Pair<ComboBoxPropertyEditorModel, ValueChangedListener> {
    val model = createModel()
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
    model.popupMenuWillBecomeVisible()
    assertThat(model.selectedItem.toString()).isEqualTo("visible")
  }

  @Test
  fun testSelectItemIsKeptAfterFocusLoss() {
    val model = createModel()
    model.isPopupVisible = true
    model.selectedItem = "gone"

    model.popupMenuWillBecomeInvisible(false)
    model.focusLost()

    assertThat(model.property.value).isEqualTo("gone")
  }

  @RunsInEdt
  @Test
  fun testSelectActionItemShouldNotUpdateValueOnFocusLoss() {
    ActionManager.getInstance()
    val model = createModel()
    var future: Future<*>? = null
    val action = object : AnAction() {
      override fun actionPerformed(event: AnActionEvent) {
        model.focusLost()
        future = ApplicationManager.getApplication().executeOnPooledThread { model.property.value = "gone" }
      }
    }
    ActionManager.getInstance()
    model.isPopupVisible = true
    ActionManager.getInstance()
    model.selectedItem = EnumValue.action(action)
    model.popupMenuWillBecomeInvisible(false)
    assertThat(model.property.value).isEqualTo("visible")

    // The action is executed delayed on the UI event queue:
    while (future == null) {
      UIUtil.dispatchAllInvocationEvents()
    }

    // Emulate a dialog is writing to the property long after the menu has been closed:
    future!!.get()

    assertThat(model.property.value).isEqualTo("gone")
  }

  @RunsInEdt
  @Test
  fun testSelectedItemSetOnlyOnce() {
    val model = createModel()
    val property = model.property as FakePropertyItem
    model.isPopupVisible = true
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
    val (model, listener) = createModelWithListener()
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
    model.isPopupVisible = true
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
    model.isPopupVisible = true
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
    model.isPopupVisible = true
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
    assertThat(model.size).isEqualTo(3)
    assertThat(model.getElementAt(0).toString()).isEqualTo("visible")
    assertThat(model.getElementAt(1).toString()).isEqualTo("invisible")
    assertThat(model.getElementAt(2).toString()).isEqualTo("gone")
  }

  @Test
  fun testFocusLossWillUpdateValue() {
    // setup
    val (model, listener) = createModelWithListener()
    model.focusGained()
    model.text = "#333333"

    // test
    model.focusLost()
    assertThat(model.hasFocus).isFalse()
    assertThat(model.property.value).isEqualTo("#333333")
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusLossWithUnchangedValueWillNotUpdateValue() {
    // setup
    val (model, listener) = createModelWithListener()
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
    model.selectedItem = "text"
    assertThat(listener.called).isTrue()
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
