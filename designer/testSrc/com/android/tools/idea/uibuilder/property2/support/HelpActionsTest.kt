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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_VIEWGROUP
import com.android.SdkConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.FQCN_IMAGE_VIEW
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.SdkConstants.FRAME_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.EXPECTED_TEXT_TOOLTIP
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class HelpActionsTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @RunsInEdt
  @Test
  fun testTooltipForTextProperty() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_TEXT, NlPropertyType.STRING)
    assertThat(property.tooltipForName).isEqualTo(EXPECTED_TEXT_TOOLTIP)
  }

  @RunsInEdt
  @Test
  fun testTooltipForCustomPropertyWithoutDocumentation() {
    val descriptor = ComponentDescriptor(TEXT_VIEW)
          .withBounds(0, 0, 1000, 1000)
          .wrapContentWidth()
          .wrapContentHeight()
          .withAttribute(AUTO_URI, "something", "1")
    val util = SupportTestUtil(projectRule, descriptor)
    util.setUpCustomView()
    val property = util.makeProperty(AUTO_URI, "legend", NlPropertyType.BOOLEAN)
    assertThat(property.tooltipForName).isEqualTo("<html><b>app:legend</b></html>")
  }

  @RunsInEdt
  @Test
  fun testHelp() {
    val manager = projectRule.mockProjectService(DocumentationManager::class.java)
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = FRAME_LAYOUT)
    @Suppress("DEPRECATION")
    val tag = util.components[0].tagDeprecated
    util.loadProperties()
    val context = SimpleDataContext.getSimpleContext(HelpSupport.PROPERTY_ITEM.name, util.properties[ANDROID_URI, ATTR_TEXT])
    val event = AnActionEvent.createFromDataContext("", null, context)
    HelpActions.help.actionPerformed(event)
    verify(manager).showJavaDocInfo(eq(tag), eq(tag), eq(true), isNull(), eq(EXPECTED_TEXT_TOOLTIP), eq(true))
  }

  @Test
  fun testFilterRawAttributeComment() {
    val comment = "Here is a\n" +
                  "        comment with an\n" +
                  "        odd formatting."
    assertThat(HelpActions.filterRawAttributeComment(comment)).isEqualTo("Here is a comment with an odd formatting.")
  }

  @Test
  fun testToHelpUrl() {
    assertThat(toHelpUrl(FQCN_IMAGE_VIEW, ATTR_SRC)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/widget/ImageView.html#attr_android:src")

    assertThat(toHelpUrl(FQCN_TEXT_VIEW, ATTR_FONT_FAMILY)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/widget/TextView.html#attr_android:fontFamily")

    assertThat(toHelpUrl(CLASS_VIEWGROUP, ATTR_LAYOUT_HEIGHT)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/view/ViewGroup.LayoutParams.html#attr_android:layout_height")

    assertThat(toHelpUrl(CLASS_VIEWGROUP, ATTR_LAYOUT_MARGIN_BOTTOM)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/view/ViewGroup.MarginLayoutParams.html#attr_android:layout_marginBottom")

    assertThat(toHelpUrl(CONSTRAINT_LAYOUT.oldName(), ATTR_LAYOUT_TO_END_OF)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/support/constraint/ConstraintLayout.LayoutParams.html")

    assertThat(toHelpUrl(CONSTRAINT_LAYOUT.newName(), ATTR_LAYOUT_TO_END_OF)).isEqualTo(
      "${DEFAULT_ANDROID_REFERENCE_PREFIX}androidx/constraintlayout/widget/ConstraintLayout.LayoutParams.html")

    assertThat(toHelpUrl("com.company.MyView", "my_attribute")).isNull()
  }

  private fun toHelpUrl(componentName: String, propertyName: String): String? {
    val property = mock(NlPropertyItem::class.java)
    `when`(property.name).thenReturn(propertyName)
    return HelpActions.toHelpUrl(componentName, property)
  }
}
