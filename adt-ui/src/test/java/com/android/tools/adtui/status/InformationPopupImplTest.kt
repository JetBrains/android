/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.adtui.status

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Ref
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.AnActionLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyString
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.EventQueue.invokeLater
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

  @get:Rule
  val disposableRule = DisposableRule()

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
  fun testPopupWithPopupLinks() {
    val popup = InformationPopupImpl(
      "Title",
      "A Description",
      listOf(),
      listOf(
        AnActionLink("Action 1", object : AnAction() {
          override fun actionPerformed(e: AnActionEvent) {}
        }),
        object: AnActionLink("Action 2", object : AnAction() {
          override fun actionPerformed(e: AnActionEvent) {}
        }) {}.apply { putUserData(POPUP_ACTION, true) }
      )
    )

    val parent = JPanel().apply {
      layout = BorderLayout()
      size = Dimension(200, 100)
      add(popup.popupComponent, BorderLayout.CENTER)
    }

    val fakeUi = FakeUi(parent, 1.0, true)
    popup.showPopup(disposableRule.disposable , parent)

    assertEquals(
      "Action 1, Action 2",
      fakeUi.findAllComponents<AnActionLink>().joinToString(", ") { it.text }
    )

    fakeUi.clickOn(fakeUi.findAllComponents<AnActionLink>().find { link -> link.text == "Action 2" }!!)

    val latch = CountDownLatch(1)
    val visible = Ref<Boolean>(popup.isVisible())
    assertTrue(visible.get())

    invokeLater {
      // Popup should still be showing.
      visible.set(popup.isVisible())
      latch.countDown()
    }
    latch.await(5, TimeUnit.SECONDS)
    assertTrue(visible.get())
  }

  @Test
  fun testPopupWithAdditionalActionsDisplaysAdditionalActions() {
    val spyActionManager = spy(ActionManager.getInstance())
    ApplicationManager.getApplication().replaceService(
      ActionManager::class.java, spyActionManager, disposableRule.disposable
    )

    val popup = InformationPopupImpl(
      "Title",
      "A Description",
      listOf(
        object : AnAction("Action 1") {
          override fun actionPerformed(e: AnActionEvent) {
          }
        },
        object : AnAction("Action 2") {
          override fun actionPerformed(e: AnActionEvent) {
          }
        }),
      listOf(),
    )

    val fakeUi = FakeUi(JPanel().apply {
      layout = BorderLayout()
      size = Dimension(200, 100)
      add(popup.popupComponent, BorderLayout.CENTER)
    }, 1.0, true)

    fakeUi.updateToolbars()

    val menuButton = fakeUi.findComponent(ActionButton::class.java)!!
    fakeUi.clickOn(menuButton)

    val captor = ArgumentCaptor.forClass(ActionGroup::class.java)
    verify(spyActionManager).createActionPopupMenu(anyString(), captor.capture())

    val additionalActionsGroup = captor.value
    assertEquals(
      "Action 1, Action 2",
      additionalActionsGroup.getChildren(null).joinToString(", ") { it.templateText }
    )
    assertTrue(additionalActionsGroup.isPopup)
    assertTrue(additionalActionsGroup.templatePresentation.isVisible)
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

    assertFalse(popup.hasEnteredPopup)

    // Move mouse but not inside the popup
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width * 2, 0)
    assertFalse(popup.hasEnteredPopup)

    // Move mouse into the popup
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width / 2, popup.popupComponent.y + popup.popupComponent.height / 2)
    assertTrue(popup.hasEnteredPopup)
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

    assertFalse(popup.hasEnteredPopup)

    // Move mouse but not inside the popup
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width * 2, 0)
    assertFalse(popup.hasEnteredPopup)

    // Move mouse into the popup
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width / 2, popup.popupComponent.y + popup.popupComponent.height / 2)
    assertTrue(popup.hasEnteredPopup)

    // Move back out, popup should be closed
    fakeUi.mouse.moveTo(popup.popupComponent.x + popup.popupComponent.width * 2, 0)
    assertFalse(popup.isVisible())
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