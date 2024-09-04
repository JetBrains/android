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

import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_VIEWGROUP
import com.android.SdkConstants.FQCN_IMAGE_VIEW
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.SdkConstants.FRAME_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.popup.FakeComponentPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NlPropertyDocumentationTarget
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.documentation.DocumentationEditorPane
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.documentation.DOCUMENTATION_TARGETS
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.resolvedPromise
import org.jsoup.Jsoup
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class HelpActionsTest {

  private val projectRule = AndroidProjectRule.withSdk()
  private val popupRule = JBPopupRule()

  @get:Rule val chain = RuleChain.outerRule(projectRule).around(popupRule).around(EdtRule())!!

  @Test
  fun testHelpForCustomPropertyWithoutDocumentation() = runBlocking {
    val property =
      NlPropertyItem(
        AUTO_URI,
        "legend",
        NlPropertyType.BOOLEAN,
        null,
        "",
        "",
        mock(),
        mock(),
        null,
        null,
        supervisorScope = this,
      )

    withContext(uiThread) {
      assertThat(helpTextInPopup(property))
        .isEqualTo(
          // language=HTML
          """
            <html>
             <head></head>
             <body>
              <div class="content">
               <p><b>legend</b><br><br></p>
              </div>
             </body>
            </html>
          """
            .trimIndent()
        )
    }
  }

  @RunsInEdt
  @Test
  fun testHelp() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = FRAME_LAYOUT)
    util.loadProperties()
    val property = util.properties[ANDROID_URI, ATTR_TEXT]
    assertThat(helpTextInPopup(property))
      .isEqualTo(
        // language=HTML
        """
        <html>
         <head></head>
         <body>
          <div class="content">
           <p><b>android:text</b><br><br>
            Formats: string<br><br>
            Text to display.</p>
          </div>
         </body>
        </html>
      """
          .trimIndent()
      )
  }

  private fun helpTextInPopup(property: NlPropertyItem): String {
    val context =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(
          DOCUMENTATION_TARGETS,
          listOf(NlPropertyDocumentationTarget(property.model) { resolvedPromise(property) }),
        )
        .build()
    val event = AnActionEvent.createFromDataContext("", null, context)
    HelpActions.help.actionPerformed(event)
    waitForCondition(10, TimeUnit.SECONDS) { popupRule.fakePopupFactory.popupCount > 0 }
    val popup = popupRule.fakePopupFactory.getNextPopup<Unit, FakeComponentPopup>()
    val doc =
      UIUtil.findComponentsOfType(popup.contentPanel, DocumentationEditorPane::class.java)
        .singleOrNull() ?: error("No doc?")
    Disposer.dispose(popup)

    return Jsoup.parse(doc.text).html()
  }

  @Test
  fun testFilterRawAttributeComment() {
    val comment = "Here is a\n" + "        comment with an\n" + "        odd formatting."
    assertThat(HelpActions.filterRawAttributeComment(comment))
      .isEqualTo("Here is a comment with an odd formatting.")
  }

  @Test
  fun testToHelpUrl() {
    assertThat(toHelpUrl(FQCN_IMAGE_VIEW, ATTR_SRC))
      .isEqualTo(
        "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/widget/ImageView.html#attr_android:src"
      )

    assertThat(toHelpUrl(FQCN_TEXT_VIEW, ATTR_FONT_FAMILY))
      .isEqualTo(
        "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/widget/TextView.html#attr_android:fontFamily"
      )

    assertThat(toHelpUrl(CLASS_VIEWGROUP, ATTR_LAYOUT_HEIGHT))
      .isEqualTo(
        "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/view/ViewGroup.LayoutParams.html#attr_android:layout_height"
      )

    assertThat(toHelpUrl(CLASS_VIEWGROUP, ATTR_LAYOUT_MARGIN_BOTTOM))
      .isEqualTo(
        "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/view/ViewGroup.MarginLayoutParams.html#attr_android:layout_marginBottom"
      )

    assertThat(toHelpUrl(CONSTRAINT_LAYOUT.oldName(), ATTR_LAYOUT_TO_END_OF))
      .isEqualTo(
        "${DEFAULT_ANDROID_REFERENCE_PREFIX}android/support/constraint/ConstraintLayout.LayoutParams.html"
      )

    assertThat(toHelpUrl(CONSTRAINT_LAYOUT.newName(), ATTR_LAYOUT_TO_END_OF))
      .isEqualTo(
        "${DEFAULT_ANDROID_REFERENCE_PREFIX}androidx/constraintlayout/widget/ConstraintLayout.LayoutParams.html"
      )

    assertThat(toHelpUrl("com.company.MyView", "my_attribute")).isNull()
  }

  private fun toHelpUrl(componentName: String, propertyName: String): String? {
    val property: NlPropertyItem = mock()
    whenever(property.name).thenReturn(propertyName)
    return HelpActions.toHelpUrl(componentName, property)
  }
}
