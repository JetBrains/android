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
package com.android.tools.idea.uibuilder.property.support

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcechooser.common.ResourcePickerSources
import com.android.tools.idea.uibuilder.property.NlNewPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.impl.support.PropertiesTableImpl
import com.google.common.collect.HashBasedTable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Color
import java.awt.Component
import java.awt.Point

@RunsInEdt
class ResourceActionsTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private var colorPicker: TestColorPicker? = null

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @After
  fun cleanUp() {
    colorPicker = null
  }

  @Test
  fun testOpenResourceActionWithInvalidXmlTag() {
    val action = OpenResourceManagerAction
    val util = SupportTestUtil(projectRule, SdkConstants.TEXT_VIEW)
    val property = util.makeProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT, NlPropertyType.STRING)
    val context = SimpleDataContext.getSimpleContext(HelpSupport.PROPERTY_ITEM.name, property)
    val event = AnActionEvent.createFromDataContext("", null, context)
    deleteXmlTag(property)

    // Expect the dialog not to be displayed, because the tag is now gone.
    // Displaying the resource picker would cause an exception since no UI is available in headless mode.
    action.actionPerformed(event)
  }

  @Test
  fun testUseColorPicker() {
    val action = TestableColorSelectionAction(::createColorPicker)
    val util = SupportTestUtil(projectRule, SdkConstants.TEXT_VIEW)
    val property = util.makeProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_COLOR, NlPropertyType.COLOR)

    // Verify that the textColor is not set:
    assertThat(property.value).isNull()

    val context = SimpleDataContext.getSimpleContext(HelpSupport.PROPERTY_ITEM.name, property)
    val event = AnActionEvent.createFromDataContext("", null, context)
    action.actionPerformed(event)
    val picker = colorPicker ?: error("colorPicker expected")

    // The real color picker will set an initial color of WHITE if no initialColor was specified.
    // We do not want that, so make sure there is an initial color even if the property is not set yet.
    assertThat(picker.initialColor).isEqualTo(Color.WHITE)

    // Now set a new color
    colorPicker!!.colorPickedCallback!!.invoke(Color.BLUE)
    assertThat(property.value).isEqualTo("#0000FF")
  }

  @Test
  fun testUseColorPickerWithNewProperty() {
    val action = TestableColorSelectionAction(::createColorPicker)
    val util = SupportTestUtil(projectRule, SdkConstants.TEXT_VIEW)
    val actualProperty = util.makeProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_COLOR, NlPropertyType.COLOR)
    val properties: PropertiesTable<NlPropertyItem> =
      PropertiesTableImpl<NlPropertyItem>(HashBasedTable.create()).also { it.put(actualProperty) }
    val property = NlNewPropertyItem(util.model, properties)
    property.name = "${SdkConstants.PREFIX_ANDROID}${SdkConstants.ATTR_TEXT_COLOR}"

    // Verify that the textColor is not set:
    assertThat(property.delegate).isEqualTo(actualProperty)

    val context = SimpleDataContext.getSimpleContext(HelpSupport.PROPERTY_ITEM.name, property)
    val event = AnActionEvent.createFromDataContext("", null, context)
    action.actionPerformed(event)
    val picker = colorPicker ?: error("colorPicker expected")

    // The real color picker will set an initial color of WHITE if no initialColor was specified.
    // We do not want that, so make sure there is an initial color even if the property is not set yet.
    assertThat(picker.initialColor).isEqualTo(Color.WHITE)

    // Now set a new color
    colorPicker!!.colorPickedCallback!!.invoke(Color.BLUE)
    assertThat(actualProperty.value).isEqualTo("#0000FF")

    // And set another new color
    colorPicker!!.colorPickedCallback!!.invoke(Color.CYAN)
    assertThat(actualProperty.value).isEqualTo("#00FFFF")
  }

  private fun deleteXmlTag(property: NlPropertyItem) {
    val tag = property.components.first().backend.tag!!
    WriteCommandAction.writeCommandAction(projectRule.project).run<Throwable> {
      tag.delete()
    }
  }

  private fun createColorPicker(
    initialColor: Color?,
    initialColorResource: ResourceValue?,
    configuration: Configuration?,
    resourcePickerSources: List<ResourcePickerSources>,
    restoreFocusComponent: Component?,
    locationToShow: Point?,
    colorPickedCallback: ((Color) -> Unit)?,
    colorResourcePickedCallback: ((String) -> Unit)?
  ) {
    colorPicker = TestColorPicker(initialColor, initialColorResource, configuration, resourcePickerSources, restoreFocusComponent,
                                  locationToShow, colorPickedCallback, colorResourcePickedCallback)
  }

  @Suppress("unused")
  private class TestColorPicker(
    val initialColor: Color?,
    val initialColorResource: ResourceValue?,
    val configuration: Configuration?,
    val resourcePickerSources: List<ResourcePickerSources>,
    val restoreFocusComponent: Component?,
    val locationToShow: Point?,
    val colorPickedCallback: ((Color) -> Unit)?,
    val colorResourcePickedCallback: ((String) -> Unit)?
  )
}
