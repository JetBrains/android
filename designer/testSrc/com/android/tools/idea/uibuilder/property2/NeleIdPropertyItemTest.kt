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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.*
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NeleIdPropertyItem.Companion.RefactoringChoice
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBCheckBox
import com.intellij.usageView.UsageInfo
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import javax.swing.JPanel

@RunsInEdt
class NeleIdPropertyItemTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule
  val edtRule = EdtRule()

  private var savedChoice = RefactoringChoice.ASK

  @Before
  fun setUp() {
    savedChoice = NeleIdPropertyItem.choiceForNextRename
    NeleIdPropertyItem.choiceForNextRename = RefactoringChoice.ASK
  }

  @After
  fun tearDown() {
    NeleIdPropertyItem.choiceForNextRename = savedChoice
  }

  @Test
  fun testSetValueChangeReferences() {
    val util = SupportTestUtil.fromId(projectRule, testLayout, "textView")
    val property = util.makeIdProperty()
    property.dialogProvider = { _ -> makeDialogBuilder(OK_EXIT_CODE) }
    property.value = "label"
    assertThat(property.components[0].id).isEqualTo("label")
    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/label")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/label")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/label")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/label")
  }

  @Test
  fun testSetAndroidValueChangeReferences() {
    val util = SupportTestUtil.fromId(projectRule, testLayout, "textView")
    val property = util.makeIdProperty()
    property.dialogProvider = { _ -> makeDialogBuilder(OK_EXIT_CODE) }
    property.value = "@android:id/text2"
    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@android:id/text2")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@android:id/text2")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@android:id/text2")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@android:id/text2")
  }

  @Test
  fun testSetValueDoNotChangeReferences() {
    val util = SupportTestUtil.fromId(projectRule, testLayout, "textView")
    val property = util.makeIdProperty()
    property.dialogProvider = { _ -> makeDialogBuilder(NO_EXIT_CODE) }
    property.value = "label"
    UIUtil.dispatchAllInvocationEvents()

    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/label")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/textView")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView")

    // Change id again (make sure dialog is not shown)
    property.dialogProvider = { _ -> throw RuntimeException("Dialog created unexpectedly") }
    property.value = "text"
    UIUtil.dispatchAllInvocationEvents()
  }

  @Test
  fun testSetValueAndYesToChangeReferencesAndDoNotCheckAgain() {
    val util = SupportTestUtil.fromId(projectRule, testLayout, "textView")
    val property = util.makeIdProperty()
    property.dialogProvider = { _ -> makeDialogBuilder(OK_EXIT_CODE, true) }
    property.value = "other"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/other")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/other")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/other")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/other")

    // Set id again, this time expect references to be changed without showing a dialog
    property.dialogProvider = { _ -> throw RuntimeException("Dialog created unexpectedly") }
    property.value = "last"
    UIUtil.dispatchAllInvocationEvents()

    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/last")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/last")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/last")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/last")
  }

  @Test
  fun testSetValueAndYesWillNotEnablePreviewBeforeRun() {
    val processor = mock(RenameProcessor::class.java)
    val util = SupportTestUtil.fromId(projectRule, testLayout, "textView")
    val property = util.makeIdProperty()
    `when`(processor.findUsages()).thenReturn(arrayOf(mock(UsageInfo::class.java)))
    property.dialogProvider = { _ -> makeDialogBuilder(OK_EXIT_CODE) }
    property.renameProcessorProvider = { _, _, _ -> processor }
    property.value = "label"

    val inOrder = inOrder(processor)
    inOrder.verify(processor).findUsages()
    inOrder.verify(processor).setPreviewUsages(false)
    inOrder.verify(processor).run()
  }

  @Test
  fun testSetValueAndPreviewWillEnablePreviewBeforeRun() {
    val processor = mock(RenameProcessor::class.java)
    val util = SupportTestUtil.fromId(projectRule, testLayout, "textView")
    val property = util.makeIdProperty()
    `when`(processor.findUsages()).thenReturn(arrayOf(mock(UsageInfo::class.java)))
    property.dialogProvider = { _ -> makeDialogBuilder(PREVIEW_EXIT_CODE) }
    property.renameProcessorProvider = { _, _, _ -> processor }
    property.value = "label"

    val inOrder = inOrder(processor)
    inOrder.verify(processor).findUsages()
    inOrder.verify(processor).setPreviewUsages(true)
    inOrder.verify(processor).run()
  }

  @Test
  fun testSetValueAndNoWillChangeTheValueButRenameProcessWillNotRun() {
    val processor = mock(RenameProcessor::class.java)
    val util = SupportTestUtil.fromId(projectRule, testLayout, "textView")
    val property = util.makeIdProperty()
    `when`(processor.findUsages()).thenReturn(arrayOf(mock(UsageInfo::class.java)))
    property.dialogProvider = { _ -> makeDialogBuilder(NO_EXIT_CODE) }
    property.renameProcessorProvider = { _, _, _ -> processor }
    property.value = "label"

    val inOrder = inOrder(processor)
    inOrder.verify(processor).findUsages()
    inOrder.verifyNoMoreInteractions()

    assertThat(property.components[0].id).isEqualTo("label")
  }

  @Test
  fun testSetValueAndCancelNotExecuteRenameProcess() {
    val processor = mock(RenameProcessor::class.java)
    val util = SupportTestUtil.fromId(projectRule, testLayout, "textView")
    val property = util.makeIdProperty()
    `when`(processor.findUsages()).thenReturn(arrayOf(mock(UsageInfo::class.java)))
    property.dialogProvider = { _ -> makeDialogBuilder(CANCEL_EXIT_CODE) }
    property.renameProcessorProvider = { _, _, _ -> processor }
    property.value = "label"

    val inOrder = inOrder(processor)
    inOrder.verify(processor).findUsages()
    inOrder.verifyNoMoreInteractions()

    assertThat(property.components[0].id).isEqualTo("textView")
  }

  private fun makeDialogBuilder(exitCode: Int, doNotCheckAgain: Boolean = false): DialogBuilder {
    val addAction = mock(DialogBuilder.CustomizableAction::class.java)
    val dialogBuilder = mock(DialogBuilder::class.java)
    `when`(dialogBuilder.addOkAction()).thenReturn(addAction)
    if (doNotCheckAgain) {
      doAnswer { _ ->
        val panel = ArgumentCaptor.forClass(JPanel::class.java)
        verify(dialogBuilder).setCenterPanel(panel.capture())
        for (component in panel.value.components) {
          if (component is JBCheckBox) {
            component.isSelected = true
          }
        }
        exitCode
      }.`when`(dialogBuilder).show()
    }
    else {
      `when`(dialogBuilder.show()).thenReturn(exitCode)
    }
    return dialogBuilder
  }

  @Language("XML")
  private val testLayout = """
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/textView"
        android:layout_width="100dp"
        android:layout_height="100dp"
        android:layout_alignParentLeft="true"
        android:layout_alignParentStart="true" />

    <CheckBox
        android:id="@id/checkBox1"
        android:layout_width="100dp"
        android:layout_height="100dp"
        android:layout_below="@id/textView"
        android:layout_toRightOf="@id/textView" />

    <CheckBox
        android:id="@id/checkBox2"
        android:layout_width="100dp"
        android:layout_height="100dp"
        android:layout_below="@id/button1"
        android:layout_toRightOf="@id/textView" />

</RelativeLayout>
"""
}
