/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.handlers.motion.property.testutil.MotionAttributeRule
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.property.panel.api.TableLineModel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import icons.StudioIcons
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import java.awt.Window

@RunsInEdt
class NewCustomAttributePanelTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val motionRule = MotionAttributeRule(projectRule)
  private var panelWrapper: NewCustomAttributeWrapper? = null
  private val panel: NewCustomAttributeWrapper
    get() = panelWrapper!!

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(motionRule).around(EdtRule())!!

  @Before
  fun before() {
    panelWrapper = createPanel("start", "widget")
  }

  @After
  fun after() {
    panelWrapper?.close(0)
    panelWrapper = null
  }

  @Test
  fun testAddCustomField() {
    panelWrapper?.close(0)
    panelWrapper = createPanel("start", "button")
    panel.dataTypeComboBox.selectedItem = CustomAttributeType.CUSTOM_STRING
    panel.attributeNameEditor.text = "text"
    panel.initialValueEditor.text = "Hello"
    panel.doOKAction()
    assertThat(panel.errorLabel.isVisible).isFalse()
    assertThat(motionRule.sceneFileLines(36..38)).isEqualTo("<CustomAttribute\n" +
                                                            "     motion:attributeName=\"text\"\n" +
                                                            "     motion:customStringValue=\"Hello\" />")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set CustomAttribute.text to Hello")
    assertThat(panel.isOK).isTrue()

    // The new property must be added to the model properties by now:
    assertThat(motionRule.properties).containsKey(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)
    assertThat(motionRule.properties[MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE]?.get("", "text")).isNotNull()
  }

  @Test
  fun testValueError() {
    panel.dataTypeComboBox.selectedItem = CustomAttributeType.CUSTOM_STRING
    panel.attributeNameEditor.text = "text"
    panel.initialValueEditor.text = "@string/my_unknown_string"
    assertThat(panel.errorLabel.isVisible).isTrue()
    assertThat(panel.errorLabel.text).isEqualTo("Cannot resolve symbol: 'my_unknown_string'")
    assertThat(panel.errorLabel.icon).isSameAs(StudioIcons.Common.ERROR_INLINE)
    assertThat(panel.isOKActionEnabled).isFalse()
  }

  @Test
  fun testMethodNameError() {
    panel.dataTypeComboBox.selectedItem = CustomAttributeType.CUSTOM_STRING
    panel.attributeNameEditor.text = "unknown_method"
    assertThat(panel.errorLabel.isVisible).isTrue()
    assertThat(panel.errorLabel.text).isEqualTo("Method not found: setUnknown_method;  check arguments")
    assertThat(panel.errorLabel.icon).isSameAs(StudioIcons.Common.ERROR_INLINE)
    assertThat(panel.isOKActionEnabled).isFalse()
  }

  @Test
  fun testAcceptAnywayWithMethodNameError() {
    panel.dataTypeComboBox.selectedItem = CustomAttributeType.CUSTOM_STRING
    panel.attributeNameEditor.text = "unknown_method"
    panel.initialValueEditor.text = "Hello"
    panel.acceptAnyway.doClick()
    assertThat(panel.errorLabel.isVisible).isTrue()
    assertThat(panel.errorLabel.text).isEqualTo("Method not found: setUnknown_method;  check arguments")
    assertThat(panel.errorLabel.icon).isSameAs(StudioIcons.Common.ERROR_INLINE)
    assertThat(panel.isOKActionEnabled).isTrue()
  }

  @Suppress("SameParameterValue")
  private fun createPanel(setId: String, id: String): NewCustomAttributeWrapper {
    motionRule.selectConstraint(setId, id)
    val component = motionRule.selection.componentForCustomAttributeCompletions!!
    val textView = mock(android.widget.TextView::class.java)
    component.viewInfo = ViewInfo("TextView", null, 0, 0, 30, 20, textView, null, null)
    val tableModel = mock(TableLineModel::class.java)
    return NewCustomAttributeWrapper(motionRule.attributesModel, motionRule.selection, tableModel)
  }

  private class NewCustomAttributeWrapper(
    propertiesModel: MotionLayoutAttributesModel,
    selection: MotionSelection,
    lineModel: TableLineModel
  ) : NewCustomAttributePanel(propertiesModel, selection, lineModel) {
    private lateinit var window: Window

    override fun getWindow(): Window {
      window = mock(Window::class.java)
      return window
    }
  }
}
