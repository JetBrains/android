/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.NavActionsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.scene.targets.ActionTarget
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import org.junit.Assert.assertArrayEquals
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent

class NavActionsInspectorProviderTest : NavTestCase() {
  fun testIsApplicable() {
    val provider = NavActionsInspectorProvider()
    val surface = mock(NavDesignSurface::class.java)
    val manager = NavPropertiesManager(myFacet, surface)
    val component1 = mock(NlComponent::class.java)
    val component2 = mock(NlComponent::class.java)
    // Simple case: one component, actions property
    assertTrue(provider.isApplicable(listOf(component1), mapOf("Actions" to NavActionsProperty(listOf(component1))), manager))
    // One component, actions + other property
    assertTrue(provider.isApplicable(listOf(component1),
        mapOf("Actions" to NavActionsProperty(listOf(component1)), "foo" to mock(NlProperty::class.java)), manager))
    // Two components
    assertFalse(provider.isApplicable(listOf(component1, component2),
        mapOf("Actions" to NavActionsProperty(listOf(component1, component2))), manager))
    // zero components
    assertFalse(provider.isApplicable(listOf(), mapOf("Actions" to NavActionsProperty(listOf())), manager))
    // Non-actions property only
    assertFalse(provider.isApplicable(listOf(component1), mapOf("foo" to mock(NlProperty::class.java)), manager))
    Disposer.dispose(surface)
  }

  fun testListContent() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent("f1")
                .unboundedChildren(
                    actionComponent("a1").withDestinationAttribute("f2"),
                    actionComponent("a2").withDestinationAttribute("activity")),
            fragmentComponent("f2"),
            activityComponent("activity")))
        .build()

    val manager = mock(NavPropertiesManager::class.java)
    val navInspectorProviders = spy(NavInspectorProviders(manager, myRootDisposable))
    `when`(navInspectorProviders.providers).thenReturn(listOf(NavActionsInspectorProvider()))
    `when`(manager.getInspectorProviders(any())).thenReturn(navInspectorProviders)
    `when`(manager.facet).thenReturn(myFacet)

    val panel = NavInspectorPanel(myRootDisposable)
    panel.setComponent(listOf(model.find("f1")!!), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    val actionsList = flatten(panel).find { it.name == NAV_LIST_COMPONENT_NAME }!! as JBList<NlProperty>

    assertEquals(2, actionsList.itemsCount)
    val propertiesList = listOf(actionsList.model.getElementAt(0), actionsList.model.getElementAt(1))
    assertSameElements(propertiesList.map { it.components[0].id }, listOf("a1", "a2"))
    assertSameElements(propertiesList.map { it.name }, listOf("f2", "activity"))
  }

  fun testPopupContents() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent("f1")
                .unboundedChildren(
                    actionComponent("a1").withDestinationAttribute("f2"),
                    actionComponent("a2").withDestinationAttribute("activity")),
            fragmentComponent("f2"),
            activityComponent("activity")))
        .build()

    val manager = mock(NavPropertiesManager::class.java)
    val navInspectorProviders = spy(NavInspectorProviders(manager, myRootDisposable))
    val provider = spy(NavActionsInspectorProvider())
    `when`(navInspectorProviders.providers).thenReturn(listOf(provider))
    `when`(manager.getInspectorProviders(any())).thenReturn(navInspectorProviders)
    `when`(manager.facet).thenReturn(myFacet)

    @Suppress("UNCHECKED_CAST")
    val answer = object: Answer<NavListInspectorProvider.NavListInspectorComponent<NavActionsProperty>> {
      var result: NavListInspectorProvider.NavListInspectorComponent<NavActionsProperty>? = null

      override fun answer(invocation: InvocationOnMock?): NavListInspectorProvider.NavListInspectorComponent<NavActionsProperty> =
          (invocation?.callRealMethod() as NavListInspectorProvider.NavListInspectorComponent<NavActionsProperty>).also { result = it }
    }
    doAnswer(answer).`when`(provider).createCustomInspector(any(), any(), any())
    val panel = NavInspectorPanel(myRootDisposable)
    panel.setComponent(listOf(model.find("f1")!!), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    var actionsList = flatten(panel).find { it.name == NAV_LIST_COMPONENT_NAME } as JBList<NlProperty>
    actionsList = spy(actionsList)
    `when`(actionsList.isShowing).thenReturn(true)

    val cell0Location = actionsList.indexToLocation(0)

    actionsList.selectedIndices = intArrayOf(0)
    var group: ActionGroup = answer.result?.createPopupContent(MouseEvent(
        actionsList, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
        cell0Location.x, cell0Location.y, 1, true))!!

    assertEquals(3, group.getChildren(null).size)
    assertEquals("Edit", group.getChildren(null)[0].templatePresentation.text)
    assertInstanceOf(group.getChildren(null)[1], Separator::class.java)
    assertEquals("Delete", group.getChildren(null)[2].templatePresentation.text)
    assertArrayEquals(intArrayOf(0), actionsList.selectedIndices)

    actionsList.selectedIndices = intArrayOf(0, 1)
    group = answer.result?.createPopupContent(MouseEvent(
        actionsList, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
        cell0Location.x, cell0Location.y, 1, true))!!

    assertEquals(1, group.getChildren(null).size)
    assertEquals("Delete", group.getChildren(null)[0].templatePresentation.text)
    assertArrayEquals(intArrayOf(0, 1), actionsList.selectedIndices)

    actionsList.selectedIndices = intArrayOf(1)
    group = answer.result?.createPopupContent(MouseEvent(
        actionsList, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
        cell0Location.x, cell0Location.y, 1, true))!!

    assertEquals(3, group.getChildren(null).size)
    assertArrayEquals(intArrayOf(0), actionsList.selectedIndices)
  }

  fun testSelectionHighlighted() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent("f1")
                .unboundedChildren(
                    actionComponent("a1").withDestinationAttribute("f2"),
                    actionComponent("a2").withDestinationAttribute("activity")),
            fragmentComponent("f2"),
            activityComponent("activity")))
        .build()

    val manager = mock(NavPropertiesManager::class.java)
    val navInspectorProviders = spy(NavInspectorProviders(manager, myRootDisposable))
    `when`(navInspectorProviders.providers).thenReturn(listOf(NavActionsInspectorProvider()))
    `when`(manager.getInspectorProviders(any())).thenReturn(navInspectorProviders)
    `when`(manager.facet).thenReturn(myFacet)
    `when`(manager.designSurface).thenReturn(model.surface)

    val panel = NavInspectorPanel(myRootDisposable)
    val f1 = model.find("f1")!!
    panel.setComponent(listOf(f1), HashBasedTable.create<String, String, NlProperty>(), manager)

    @Suppress("UNCHECKED_CAST")
    val actionsList = flatten(panel).find { it.name == NAV_LIST_COMPONENT_NAME }!! as JBList<NlProperty>
    actionsList.addSelectionInterval(1, 1)

    val highlightedTargets = model.surface.scene!!.getSceneComponent("f1")!!.targets!!.filter { it is ActionTarget && it.isHighlighted }
    assertEquals(1, highlightedTargets.size)
    assertEquals("a1", (highlightedTargets[0] as ActionTarget).id)
  }

  fun testPlusContents() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent("f1"),
            navigationComponent("subnav")))
        .build()

    val provider = NavActionsInspectorProvider()
    val surface = model.surface as NavDesignSurface
    val actions = provider.getPopupActions(listOf(model.find("f1")!!), null, surface)
    assertEquals(4, actions.size)
    assertEquals("Add Action...", actions[0].templatePresentation.text)
    assertEquals("Return to Source...", actions[1].templatePresentation.text)
    assertInstanceOf(actions[2], Separator::class.java)
    assertEquals("Add Global...", actions[3].templatePresentation.text)

    `when`(surface.currentNavigation).thenReturn(model.find("subnav"))
    val rootActions = provider.getPopupActions(listOf(model.find("subnav")!!), null, surface)
    assertEquals(2, rootActions.size)
    assertEquals("Add Action...", rootActions[0].templatePresentation.text)
    assertEquals("Return to Source...", rootActions[1].templatePresentation.text)
  }
}

private fun <T> any(): T {
  Mockito.any<T>()
  return uninitialized()
}
private fun <T> uninitialized(): T = null as T

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}