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

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START
import com.android.SdkConstants.ATTR_LAYOUT_BELOW
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.CHECK_BOX
import com.android.SdkConstants.RELATIVE_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.uibuilder.property2.support.NeleIdRenameProcessor
import com.android.tools.idea.uibuilder.property2.support.NeleIdRenameProcessor.RefactoringChoice
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@RunsInEdt
class NeleIdPropertyItemTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule
  val edtRule = EdtRule()

  private var componentStack: ComponentStack? = null

  @Before
  fun setUp() {
    NeleIdRenameProcessor.choiceForNextRename = RefactoringChoice.ASK
    componentStack = ComponentStack(projectRule.project)
  }

  @After
  fun tearDown() {
    NeleIdRenameProcessor.choiceForNextRename = RefactoringChoice.ASK
    componentStack!!.restoreComponents()
    componentStack = null
  }

  @Test
  fun testSetValueChangeReferences() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.YES }
    property.value = "label"
    assertThat(property.components[0].id).isEqualTo("label")
    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/label")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/label")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/label")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/label")
  }

  @Test
  fun testSetAndroidValueChangeReferences() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ ->  RefactoringChoice.YES }
    property.value = "@android:id/text2"
    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@android:id/text2")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@android:id/text2")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@android:id/text2")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@android:id/text2")
  }

  @Test
  fun testSetValueDoNotChangeReferences() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.NO }
    property.value = "label"
    UIUtil.dispatchAllInvocationEvents()

    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/label")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/textView")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView")

    // Change id again (make sure dialog is not shown)
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> throw RuntimeException("Dialog created unexpectedly") }
    property.value = "text"
    UIUtil.dispatchAllInvocationEvents()
  }

  @Test
  fun testSetValueAndYesToChangeReferencesAndDoNotCheckAgain() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.YES }
    property.value = "other"
    UIUtil.dispatchAllInvocationEvents()
    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/other")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/other")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/other")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/other")

    // Set id again, this time expect references to be changed without showing a dialog
    NeleIdRenameProcessor.choiceForNextRename = RefactoringChoice.YES
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> throw RuntimeException("Dialog created unexpectedly") }
    property.value = "last"
    UIUtil.dispatchAllInvocationEvents()

    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/last")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/last")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/last")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/last")
  }

  @Test
  fun testSetValueAndYesWillNotEnablePreviewBeforeRun() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.YES }
    BaseRefactoringProcessor.runWithDisabledPreview<RuntimeException> { property.value = "label" }

    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/label")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/label")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/label")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/label")
  }

  @Test
  fun testSetValueAndPreviewWillEnablePreviewBeforeRun() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.PREVIEW }
    try {
      BaseRefactoringProcessor.runWithDisabledPreview<RuntimeException> { property.value = "label" }
      error("Preview was not shown as expected as is emulating a click on the preview button")
    }
    catch (ex: RuntimeException) {
      assertThat(ex.message).startsWith("Unexpected preview in tests: @id/textView")
    }
  }

  @Test
  fun testSetValueAndNoWillChangeTheValueButRenameProcessWillNotRun() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.NO }
    BaseRefactoringProcessor.runWithDisabledPreview<RuntimeException> { property.value = "label" }

    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/label")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/textView")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView")
  }

  @Test
  fun testSetValueAndCancelNotExecuteRenameProcess() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.CANCEL }
    BaseRefactoringProcessor.runWithDisabledPreview<RuntimeException> { property.value = "label" }

    assertThat(property.components[0].getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/textView")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/textView")
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView")
    assertThat(util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView")
  }

  @Test
  fun testValueWithReferenceInJavaOnly() {
    // Regression test for b/120617097
    addManifest(projectRule.fixture)
    val activityFile = projectRule.fixture.addFileToProject("src/MainActivity.java", testActivity)
    projectRule.fixture.addFileToProject("gen/R.java", testRFile)
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("checkBox1").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.YES }
    property.value = "checkBox30"

    // Verify that the reference in the activity file was renamed
    assertThat(activityFile.text).contains("findViewById(R.id.checkBox30)")
  }

  @Test
  fun testNoValueToolTip() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    assertThat(property.tooltipForValue).isEqualTo("")
  }

  @Test
  fun testValidation() {
    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    assertThat(property.editingSupport.validation("")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.editingSupport.validation("@+id/hello")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.editingSupport.validation("@id/hello")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.editingSupport.validation("@string/hello")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.editingSupport.validation("hello")).isEqualTo(EDITOR_NO_ERROR)
  }

  @Test
  fun testSetValueIgnoredDuringUndo() {
    val undoManager = Mockito.mock(UndoManagerImpl::class.java)
    componentStack!!.registerComponentImplementation(UndoManager::class.java, undoManager)
    Mockito.`when`(undoManager.isUndoInProgress).thenReturn(true)

    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.YES }
    property.value = "other"
    assertThat(property.value).isEqualTo("textView")
  }

  @Test
  fun testSetValueIgnoredDuringRedo() {
    val undoManager = Mockito.mock(UndoManagerImpl::class.java)
    componentStack!!.registerComponentImplementation(UndoManager::class.java, undoManager)
    Mockito.`when`(undoManager.isRedoInProgress).thenReturn(true)

    val util = SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()
    NeleIdRenameProcessor.dialogProvider = { _, _, _, _ -> RefactoringChoice.YES }
    property.value = "other"
    assertThat(property.value).isEqualTo("textView")
  }

  private fun createTestLayout(): ComponentDescriptor =
    ComponentDescriptor(RELATIVE_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        ComponentDescriptor(TEXT_VIEW)
          .withBounds(0, 0, 100, 100)
          .id("@+id/textView")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_LEFT, "true")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_START, "true"),
        ComponentDescriptor(CHECK_BOX)
          .withBounds(0, 0, 200, 200)
          .id("@+id/checkBox1")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW, "@id/textView")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF, "@id/textView"),
        ComponentDescriptor(CHECK_BOX)
          .withBounds(0, 0, 200, 300)
          .id("@+id/checkBox2")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW, "@id/button1")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF, "@id/textView"))

  @Language("JAVA")
  private val testActivity = """
    package com.example;

    import android.app.Activity;
    import android.os.Bundle;
    import android.widget.CheckBox;

    public class MainActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.a_layout);
            CheckBox toolbar = findViewById(R.id.checkBox1);
        }
    }
    """.trimIndent()

  @Language("JAVA")
  private val testRFile = """
    package com.example;

    public final class R {
        public static final class id {
            public static final int textView=0x7f030001;
            public static final int checkBox1=0x7f030002;
            public static final int checkBox2=0x7f030003;
        }
        public static final class layout {
            public static final int a_layout=0x7f030005;
        }
    }
    """.trimIndent()
}
