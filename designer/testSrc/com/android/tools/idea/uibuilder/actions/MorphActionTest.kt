/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeComponentPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponentBackend
import com.android.tools.idea.common.model.NlComponentBackendXml
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import javax.swing.JButton
import javax.swing.JList
import kotlin.test.assertEquals

class MorphComponentActionTest {
  private val androidProjectRule = AndroidProjectRule.inMemory()
  private val popupRule = JBPopupRule()

  @get:Rule
  val rule = RuleChain(androidProjectRule, popupRule)

  @Test
  fun testPopupTrigger() {
    val xmlTag = runReadAction {
      XmlTagUtil.createTag(
        androidProjectRule.project,
        //language=xml
        """
            <LinearLayout
              xmlns:android="http://schemas.android.com/apk/res/android"
              android:attribute="value"
              android:attribute2="value2">
            </LinearLayout>
        """.trimIndent())
    }

    val model = Mockito.mock(NlModel::class.java)
    Mockito.`when`(model.project).thenReturn(androidProjectRule.project)
    Mockito.`when`(model.facet).thenReturn(androidProjectRule.module.androidFacet!!)

    val component = Mockito.mock(NlComponent::class.java)
    Mockito.`when`(component.model).thenReturn(model)
    Mockito.`when`(component.tagName).thenReturn("Button")
    Mockito.`when`(component.children).thenReturn(listOf())
    Mockito.`when`(component.childCount).thenReturn(0)
    Mockito.`when`(component.tagDeprecated).thenReturn(xmlTag)
    Mockito.`when`(component.backend).thenReturn(NlComponentBackendXml.getForTest(androidProjectRule.project, xmlTag))

    val morphComponentAction = MorphComponentAction(component) {
      listOf(
        "Suggestion1",
        "Suggestion2",
        "Suggestion3"
      )
    }

    invokeAndWaitIfNeeded {
      morphComponentAction.actionPerformed(TestActionEvent())
    }

    val fakeComponentPopup = popupRule.fakePopupFactory.getPopup<Unit>(0) as FakeComponentPopup
    val fakeUi =
      FakeUi(fakeComponentPopup.contentPanel)
    val componentList = fakeUi.findComponent<JList<*>>()!!
    val componentListString = StringBuilder()
    for (i in 0 until componentList.model.size) {
      if (componentList.selectedIndex == i) {
        componentListString.append(">")
      }
      componentListString
        .appendLine(componentList.model.getElementAt(i).toString())
    }
    assertEquals(
      """
        >Suggestion1
        Suggestion2
        Suggestion3
      """.trimIndent(), componentListString.toString().trim()
    )

    fakeUi.findComponent<JButton> { it.text == "Apply" }!!.also {
      invokeAndWaitIfNeeded {
        it.doClick()
      }
    }

    assertEquals(
      """
        <Suggestion1
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:attribute="value"
          android:attribute2="value2">
        </Suggestion1>
      """.trimIndent(),
      runReadAction { xmlTag.text }
    )

  }
}