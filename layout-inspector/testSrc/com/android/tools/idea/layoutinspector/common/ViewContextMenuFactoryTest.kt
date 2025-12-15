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
package com.android.tools.idea.layoutinspector.common

import com.android.flags.junit.FlagRule
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.mockito.kotlin.getTypedArgument
import com.android.resources.ResourceType
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.devtools.ChromeDevTools
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.COMPOSE3
import com.android.tools.idea.layoutinspector.model.COMPOSE4
import com.android.tools.idea.layoutinspector.model.COMPOSE5
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.VIEW4
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.layoutinspector.ui.LAYOUT_INSPECTOR_DATA_KEY
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Condition
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import javax.swing.JComponent
import javax.swing.JPopupMenu
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever

class ViewContextMenuFactoryTest {
  private val disposableRule = DisposableRule()
  private val flagRule1 = FlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_STATE_READS, true)
  private val flagRule2 = FlagRule(StudioFlags.DYNAMIC_LAYOUT_CHROME_DEVTOOLS_MENU, true)

  @get:Rule val rule = RuleChain(ApplicationRule(), disposableRule, flagRule1, flagRule2)

  private var source: JComponent? = mock()
  private var popupMenuComponent: JPopupMenu? = mock()
  private var createdGroup: ActionGroup? = null

  private lateinit var model: InspectorModel
  private lateinit var mockLayoutInspector: LayoutInspector
  private lateinit var event: AnActionEvent

  @Suppress("UnstableApiUsage")
  @Before
  fun setUp() {
    val disposable = disposableRule.disposable
    val mockActionManager: ActionManagerImpl = mock()
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, mockActionManager, disposable)
    val mockPopupMenu: ActionPopupMenu = mock()
    whenever(mockActionManager.createActionPopupMenu(any(), any())).thenAnswer { invocation ->
      createdGroup = invocation.getTypedArgument(1)
      mockPopupMenu
    }
    whenever(mockActionManager.performWithActionCallbacks(any(), any(), any())).thenCallRealMethod()
    whenever(mockPopupMenu.component).thenReturn(popupMenuComponent)
    model =
      model(disposable) {
        view(
          ROOT,
          viewId = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "rootId"),
        ) {
          view(VIEW1) {
            compose(COMPOSE1, "Row") {
              compose(COMPOSE2, "Item") { compose(COMPOSE3, "Text") }
              compose(COMPOSE4, "Item") { compose(COMPOSE5, "Text") }
            }
          }
          view(VIEW2, qualifiedName = "viewName") {
            view(VIEW3, textValue = "myText") { image() }
            image()
          }
          view(VIEW4, qualifiedName = "MyWebView", isDerivedFromWebView = true)
        }
      }

    val client: InspectorClient = mock()
    whenever(client.capabilities)
      .thenReturn(setOf(Capability.SUPPORTS_SKP, Capability.CAN_OBSERVE_RECOMPOSE_STATE_READS))
    mockLayoutInspector = mock()
    whenever(mockLayoutInspector.currentClient).thenReturn(client)
    val context =
      SimpleDataContext.builder().add(LAYOUT_INSPECTOR_DATA_KEY, mockLayoutInspector).build()
    event = createFakeEvent(context)
  }

  @After
  fun tearDown() {
    source = null
    popupMenuComponent = null
    createdGroup = null
  }

  @Test
  fun testEmptyModel() {
    val selectedView = model[VIEW1]!!
    showViewContextMenu(selectedView, listOf(), model(disposableRule.disposable) {}, source!!, 0, 0)
    assertThat(createdGroup).isNull()
  }

  @Test
  fun testNoViews() {
    model.hideSubtree(model[VIEW1]!!)
    model.hideSubtree(model[VIEW3]!!)
    showViewContextMenu(null, listOf(), model, source!!, 123, 456)
    val actions = createdGroup?.children(event)
    assertThat(actions?.size).isEqualTo(4)
    val createdAction = actions?.get(1)!!
    assertThat(createdAction.templateText).isEqualTo("Show All")
    ActionUtil.performAction(createdAction, event)
    assertThat(model.root.flattenedList().all { model.isVisible(it) }).isTrue()

    verify(popupMenuComponent!!).show(source, 123, 456)
  }

  @Test
  fun testOneView() {
    model.setSelection(model[VIEW2], origin = SelectionOrigin.INTERNAL)
    showViewContextMenu(model.selection!!, listOf(model[VIEW2]!!), model, source!!, 0, 0)
    assertThat(createdGroup?.children(event)?.map { it.templateText })
      .containsExactly(
        "Open Chrome DevTools",
        "Hide Subtree",
        "Show Subtree",
        "Show Only Subtree",
        "Show Only Parents",
        "Show All",
        null, // Separator
        "Go To Declaration",
      )
      .inOrder()

    val hideSubtree = createdGroup?.children(event)?.get(1)!!
    ActionUtil.performAction(hideSubtree, event)

    assertThat(model.root.flattenedList().filter { model.isVisible(it) }.map { it.drawId }.toList())
      .containsExactly(ROOT, VIEW1, VIEW4, COMPOSE1, COMPOSE2, COMPOSE3, COMPOSE4, COMPOSE5, -1L)

    val showSubTree = createdGroup?.children(event)?.get(2)!!
    ActionUtil.performAction(showSubTree, event)

    assertThat(model.root.flattenedList().filter { model.isVisible(it) }.map { it.drawId }.toList())
      .containsExactly(
        ROOT,
        VIEW1,
        VIEW2,
        VIEW3,
        VIEW4,
        COMPOSE1,
        COMPOSE2,
        COMPOSE3,
        COMPOSE4,
        COMPOSE5,
        -1L,
      )

    model.hideSubtree(model[VIEW1]!!)
    model.hideSubtree(model[VIEW3]!!)
    val showOnlySubtree = createdGroup?.children(event)?.get(3)!!
    ActionUtil.performAction(showOnlySubtree, event)

    assertThat(model.root.flattenedList().filter { model.isVisible(it) }.map { it.drawId }.toList())
      .containsExactly(VIEW2, VIEW3)

    model.showAll()
    val showOnlyParents = createdGroup?.children(event)?.get(4)!!
    ActionUtil.performAction(showOnlyParents, event)

    assertThat(model.root.flattenedList().filter { model.isVisible(it) }.map { it.drawId }.toList())
      .containsExactly(ROOT, VIEW2, -1L)
  }

  @Test
  fun testOneComposeView() {
    model.setSelection(model[COMPOSE2], origin = SelectionOrigin.INTERNAL)
    showViewContextMenu(model.selection!!, listOf(model[COMPOSE2]!!), model, source!!, 0, 0)
    assertThat(createdGroup?.children(event)?.map { it.templateText })
      .containsExactly(
        "Open Chrome DevTools",
        "Hide Subtree",
        "Show Subtree",
        "Show Only Subtree",
        "Show Only Parents",
        "Show All",
        null, // Separator
        "Observe Recomposition",
        "Go To Declaration",
      )
      .inOrder()
  }

  @Test
  fun testActionsVisibility() = withEmbeddedLayoutInspector {
    model.setSelection(model[VIEW2], origin = SelectionOrigin.INTERNAL)
    showViewContextMenu(model.selection!!, listOf(model[VIEW2]!!), model, source!!, 0, 0)
    val actions = createdGroup?.children(event)?.toList()

    actions?.forEach {
      when (it.templateText) {
        "Open Chrome DevTools" -> it.checkVisibility(event, expected = false)
        "Show All" -> it.checkIsVisible(event)
        "Hide Subtree" -> it.checkIsVisible(event)
        "Show Only Subtree" -> it.checkIsVisible(event)
        "Show Only Parents" -> it.checkIsVisible(event)
        "Show Subtree" -> it.checkIsVisible(event)
        else -> it.checkIsVisible(event)
      }
    }
  }

  @Test
  fun testMultipleViews() {
    model.setSelection(model[ROOT], origin = SelectionOrigin.INTERNAL)
    showViewContextMenu(
      model.selection!!,
      model.root.flattenedList().filter { it.drawId in listOf(ROOT, VIEW2, VIEW3) }.toList(),
      model,
      source!!,
      0,
      0,
    )
    assertThat(createdGroup?.children(event)?.map { it.templateText })
      .containsExactly(
        "Open Chrome DevTools",
        "Select View",
        "Hide Subtree",
        "Show Subtree",
        "Show Only Subtree",
        "Show Only Parents",
        "Show All",
        null, // Separator
        "Go To Declaration",
      )
      .inOrder()

    val selectView = createdGroup?.children(event)?.get(1)!!
    val views = (selectView as DropDownAction).children(event)
    assertThat(views.map { it.templateText })
      .containsExactly("myText", "viewName", "rootId")
      .inOrder()

    ActionUtil.performAction(views[0], event)
    assertThat(model.selection).isEqualTo(model[VIEW3])
    ActionUtil.performAction(views[1], event)
    assertThat(model.selection).isEqualTo(model[VIEW2])
    ActionUtil.performAction(views[2], event)
    assertThat(model.selection).isEqualTo(model[ROOT])
  }

  @Test
  fun testShowSubtreeActionEnablement() {
    model.setSelection(model[VIEW2], origin = SelectionOrigin.INTERNAL)
    showViewContextMenu(model.selection!!, listOf(model[VIEW2]!!), model, source!!, 0, 0)
    assertThat(createdGroup?.children(event)?.map { it.templateText })
      .containsExactly(
        "Open Chrome DevTools",
        "Hide Subtree",
        "Show Subtree",
        "Show Only Subtree",
        "Show Only Parents",
        "Show All",
        null, // Separator
        "Go To Declaration",
      )
      .inOrder()
    val showSubTree = createdGroup?.children(event)?.get(2)!!

    model.hideSubtree(model[VIEW3]!!)
    showSubTree.checkIsEnabled(event)

    model.showSubtree(model[VIEW2]!!)
    showSubTree.checkIsNotEnabled(event)
  }

  @Test
  fun testHideSubtreeVisibility() {
    model.setSelection(model[VIEW2], origin = SelectionOrigin.INTERNAL)
    showViewContextMenu(model.selection!!, listOf(model[VIEW2]!!), model, source!!, 0, 0)
    assertThat(createdGroup?.children(event)?.map { it.templateText })
      .containsExactly(
        "Open Chrome DevTools",
        "Hide Subtree",
        "Show Subtree",
        "Show Only Subtree",
        "Show Only Parents",
        "Show All",
        null, // Separator
        "Go To Declaration",
      )
      .inOrder()
    val hideSubtree = createdGroup?.children(event)?.get(1)!!

    model.hideSubtree(model[VIEW2]!!)
    hideSubtree.checkIsNotEnabled(event)
  }

  @Test
  fun testWebView() {
    val disposable = disposableRule.disposable
    val application = ApplicationManager.getApplication()
    application.replaceService(WebBrowserManager::class.java, WebBrowserManager(), disposable)
    application.replaceService(BrowserLauncher::class.java, mock(), disposable)
    showViewContextMenu(model[VIEW4], listOf(), model, source!!, 123, 456)
    val actions = createdGroup?.children(event)
    val action = actions!!.get(0)
    assertThat(action.templateText).isEqualTo("Open Chrome DevTools")
    val manager = WebBrowserManager.getInstance()
    val browser = manager.getBrowsers(Condition { ChromeDevTools.isChrome(it) }).single()
    browser.simulateSetActive(false)
    action.checkVisibility(event, expected = true)
    action.checkIsNotEnabled(event)
    action.checkText(event, "Open Chrome DevTools (Chrome is not installed)")

    browser.simulateSetActive(true)
    action.checkVisibility(event, expected = true)
    action.checkIsEnabled(event)
    action.checkText(event, "Open Chrome DevTools")

    ActionUtil.performAction(action, event)
    val launcher = BrowserLauncher.instance
    val url = argumentCaptor<String>()
    verify(launcher).browse(url.capture(), Mockito.eq(browser))
    assertThat(url.firstValue).isEqualTo("chrome://inspect")
  }
}

class ViewContextMenuFactoryLegacyTest {
  @get:Rule val applicationRule = ApplicationRule()

  @get:Rule val disposableRule = DisposableRule()

  private var source: JComponent? = mock()
  private var popupMenuComponent: JPopupMenu? = mock()
  private var createdGroup: ActionGroup? = null
  private lateinit var model: InspectorModel
  private lateinit var event: AnActionEvent

  @Suppress("UnstableApiUsage")
  @Before
  fun setUp() {
    model =
      model(disposableRule.disposable) {
        view(
          ROOT,
          viewId = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "rootId"),
        ) {
          view(VIEW1)
          view(VIEW2, qualifiedName = "viewName") { view(VIEW3, textValue = "myText") }
        }
      }

    val mockActionManager: ActionManagerImpl = mock()
    val mockPopupMenu: ActionPopupMenu = mock()
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, mockActionManager, disposableRule.disposable)
    whenever(mockActionManager.createActionPopupMenu(any(), any())).thenAnswer { invocation ->
      createdGroup = invocation.getTypedArgument(1)
      mockPopupMenu
    }
    whenever(mockActionManager.performWithActionCallbacks(any(), any(), any())).thenCallRealMethod()
    whenever(mockPopupMenu.component).thenReturn(popupMenuComponent)
    val client: InspectorClient = mock()
    whenever(client.capabilities).thenReturn(setOf())
    val layoutInspector: LayoutInspector = mock()
    whenever(layoutInspector.currentClient).thenReturn(client)
    val context =
      SimpleDataContext.builder().add(LAYOUT_INSPECTOR_DATA_KEY, layoutInspector).build()
    event = createFakeEvent(context)
  }

  @After
  fun tearDown() {
    source = null
    popupMenuComponent = null
    createdGroup = null
  }

  @Test
  fun testNoViews() {
    model.hideSubtree(model[VIEW1]!!)
    model.hideSubtree(model[VIEW3]!!)
    model.setSelection(model[VIEW2], origin = SelectionOrigin.INTERNAL)
    showViewContextMenu(model.selection!!, listOf(), model, source!!, 123, 456)
    assertThat(createdGroup?.children(event)?.map { it.templateText })
      .containsExactly("Open Chrome DevTools", "Go To Declaration")

    verify(popupMenuComponent!!).show(source, 123, 456)
  }

  @Test
  fun testMultipleViews() {
    model.setSelection(model[ROOT], origin = SelectionOrigin.INTERNAL)
    showViewContextMenu(
      model.selection!!,
      model.root.flattenedList().filter { it.drawId in listOf(ROOT, VIEW2, VIEW3) }.toList(),
      model,
      source!!,
      0,
      0,
    )
    assertThat(createdGroup?.children(event)?.map { it.templateText })
      .containsExactly("Open Chrome DevTools", "Select View", "Go To Declaration")
      .inOrder()

    val selectView = createdGroup?.children(event)?.get(1)!!
    val views = selectView.children(event)
    assertThat(views.map { it.templateText })
      .containsExactly("myText", "viewName", "rootId")
      .inOrder()

    ActionUtil.performAction(views[0], event)
    assertThat(model.selection).isEqualTo(model[VIEW3])
    ActionUtil.performAction(views[1], event)
    assertThat(model.selection).isEqualTo(model[VIEW2])
    ActionUtil.performAction(views[2], event)
    assertThat(model.selection).isEqualTo(model[ROOT])
  }
}

private fun createFakeEvent(context: DataContext): AnActionEvent =
  createEvent(context, null, "", ActionUiKind.NONE, null)

private fun AnAction.checkIsVisible(event: AnActionEvent) = checkVisibility(event, true)

private fun AnAction.checkVisibility(event: AnActionEvent, expected: Boolean) {
  event.presentation.copyFrom(templatePresentation)
  ActionUtil.updateAction(this, event)
  assertThat(event.presentation.isVisible).isEqualTo(expected)
}

private fun AnAction.checkIsEnabled(event: AnActionEvent) = checkEnable(event, true)

private fun AnAction.checkIsNotEnabled(event: AnActionEvent) = checkEnable(event, false)

private fun AnAction.checkEnable(event: AnActionEvent, expected: Boolean) {
  event.presentation.copyFrom(templatePresentation)
  ActionUtil.updateAction(this, event)
  assertThat(event.presentation.isEnabled).isEqualTo(expected)
}

private fun AnAction.checkText(event: AnActionEvent, expected: String) {
  event.presentation.copyFrom(templatePresentation)
  ActionUtil.updateAction(this, event)
  assertThat(event.presentation.text).isEqualTo(expected)
}

private fun AnAction.children(event: AnActionEvent): Array<AnAction> {
  val group = this as? ActionGroup ?: return emptyArray()
  @Suppress("OverrideOnly") return group.getChildren(event)
}

private fun WebBrowser.simulateSetActive(active: Boolean) {
  val method = this.javaClass.getMethod("setActive", java.lang.Boolean.TYPE)
  method.isAccessible = true
  method.invoke(this, active)
}
