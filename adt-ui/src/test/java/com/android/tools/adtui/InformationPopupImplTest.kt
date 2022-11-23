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
package com.android.tools.adtui

import com.android.tools.adtui.compose.InformationPopup
import com.android.tools.adtui.compose.InformationPopupImpl
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.AnActionLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

@RunsInEdt
class InformationPopupImplTest {
  private val projectRule = ProjectRule()
  private val popupRule = JBPopupRule()

  @get:Rule
  val rule = RuleChain(projectRule, popupRule)

  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun testPopup() {
    val popup = InformationPopupImpl(
      "Title",
      "A Description",
      listOf(),
      listOf()
    )

    val fakeUi = FakeUi(JPanel().apply {
      layout = BorderLayout()
      size = Dimension(200, 100)
      add(popup.popupComponent, BorderLayout.CENTER)
    }, 1.0, true)

    assertTrue(fakeUi.findComponent(JLabel::class.java) {
      it.text == "<html>Title</html>"
    }!!.isVisible)
    assertTrue(fakeUi.findComponent(JLabel::class.java) {
      it.text == "<html>A Description</html>"
    }!!.isVisible)
    assertTrue(fakeUi.findAllComponents(ActionButton::class.java).isEmpty())
  }

  @Test
  fun testPopupWithMenu() {
    val popup = InformationPopupImpl(
      "Title",
      "A Description",
      listOf(
        object: AnAction("Action") {
          override fun actionPerformed(e: AnActionEvent) {
          }
        }
      ),
      listOf()
    )

    val fakeUi = FakeUi(JPanel().apply {
      layout = BorderLayout()
      size = Dimension(200, 100)
      add(popup.popupComponent, BorderLayout.CENTER)
    }, 1.0, true)

    assertTrue(fakeUi.findComponent(JLabel::class.java) {
      it.text == "<html>Title</html>"
    }!!.isVisible)
    assertTrue(fakeUi.findComponent(JLabel::class.java) {
      it.text == "<html>A Description</html>"
    }!!.isVisible)

    // This must have the overflow icon
    val menuButton = fakeUi.findComponent(ActionButton::class.java)!!
    menuButton.updateIcon()
    assertEquals(AllIcons.Actions.More, menuButton.icon)
  }

  @Test
  fun testPopupWithLinks() {
    val popup = InformationPopupImpl(
      "Title",
      "A Description",
      listOf(),
      listOf(
        AnActionLink("Action 1", object : AnAction() {
          override fun actionPerformed(e: AnActionEvent) {
          }
        }),
        AnActionLink("Action 2", object : AnAction() {
          override fun actionPerformed(e: AnActionEvent) {
          }
        })
      )
    )

    val fakeUi = FakeUi(JPanel().apply {
      layout = BorderLayout()
      size = Dimension(200, 100)
      add(popup.popupComponent, BorderLayout.CENTER)
    }, 1.0, true)

    assertEquals(
      "Action 1, Action 2",
      fakeUi.findAllComponents<AnActionLink>().joinToString(", ") { it.text }
    )
  }

  @Test
  fun testMouseFromOutsideThePopupAndHoveringIntoPopup() {
    val popup = InformationPopupImpl(
      "Title",
      "A Description",
      listOf(),
      listOf()
    )

    val fakeUi = FakeUi(JPanel().apply {
      layout = BorderLayout()
      size = Dimension(200, 100)
      add(popup.popupComponent, BorderLayout.CENTER)
    }, 1.0, true)

    assertFalse(popup.shouldPopupStayOpen)

    // Move mouse but not inside the popup
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width * 2, 0)
    assertFalse(popup.shouldPopupStayOpen)

    // Move mouse into the popup
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width / 2, popup.popupComponent.y + popup.popupComponent.height / 2)
    assertTrue(popup.shouldPopupStayOpen)
  }

  @Test
  fun testHoverIntoAndOutsidePopup() {
    val popup = InformationPopupImpl(
      "Title",
      "A Description",
      listOf(),
      listOf()
    )

    val fakeUi = FakeUi(JPanel().apply {
      layout = BorderLayout()
      size = Dimension(200, 100)
      add(popup.popupComponent, BorderLayout.CENTER)
    }, 1.0, true)

    assertFalse(popup.shouldPopupStayOpen)

    // Move mouse but not inside the popup
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width * 2, 0)
    assertFalse(popup.shouldPopupStayOpen)

    // Move mouse into the popup
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width / 2, popup.popupComponent.y + popup.popupComponent.height / 2)
    assertTrue(popup.shouldPopupStayOpen)

    // Move back out, popup should be closed
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width * 2, 0)
    assertFalse(popup.shouldPopupStayOpen)
  }

  @Test
  fun callbackGetsCalledWhenMouseEntersThePopup() {
    val popup = InformationPopupImpl(
      "Title",
      "A Description",
      listOf(),
      listOf()
    )

    var isCallbackCalled = false
    popup.onMouseEnteredCallback = {
      isCallbackCalled = true
    }

    val fakeUi = FakeUi(JPanel().apply {
      layout = BorderLayout()
      size = Dimension(200, 100)
      add(popup.popupComponent, BorderLayout.CENTER)
    }, 1.0, true)

    // Move mouse but not inside the popup, callback is not called yet
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width * 2, 0)
    assertFalse(isCallbackCalled)

    // Move mouse into the popup, callback is fired
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width / 2, popup.popupComponent.y + popup.popupComponent.height / 2)
    assertTrue(isCallbackCalled)
  }
}