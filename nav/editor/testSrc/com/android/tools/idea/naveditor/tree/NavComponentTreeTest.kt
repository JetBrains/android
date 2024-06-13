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
package com.android.tools.idea.naveditor.tree

import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

class NavComponentTreeTest : NavTestCase() {
  private lateinit var model: NlModel
  private lateinit var surface: NavDesignSurface
  private lateinit var panel: TreePanel
  private lateinit var treeModel: ComponentTreeModel
  private lateinit var selectionModel: ComponentTreeSelectionModel

  override fun setUp() {
    super.setUp()

    model = model("nav.xml") {
      NavModelBuilderUtil.navigation("root") {
        fragment("fragment1") {
          deeplink("deeplink1", "www.android.com")
          argument("argument1")
          action("action1", destination = "fragment2")
        }
        fragment("fragment2")
        navigation("subnav") {
          fragment("fragment3") {
            action("action2", destination = "fragment3")
          }
        }
      }
    }

    surface = NavDesignSurface(project, myRootDisposable)
    PlatformTestUtil.waitForFuture(surface.setModel(model))

    panel = TreePanel()
    panel.setToolContext(surface)
    UIUtil.dispatchAllInvocationEvents()

    treeModel = panel.componentTreeModel
    selectionModel = panel.componentTreeSelectionModel
  }

  fun testNodes() {
    val root = (treeModel.treeRoot as? NlComponent)!!
    assertEquals(model.find("root")!!, root)

    testChildren("root", "fragment1", "fragment2", "subnav")
    testChildren("fragment1", "action1")
    testChildren("subnav", "fragment3")
    testChildren("fragment3", "action2")
  }

  private fun testChildren(id: String, vararg ids: String) {
    val component = model.find(id)!!
    val children = TreePanel.NlComponentNodeType().childrenOf(component)
    val expected = ids.map { model.find(it)!! }
    assertThat(children).containsExactlyElementsIn(expected).inOrder()
  }

  fun testSelection() {
    testSelection("root", "fragment1")
    testSelection("root", "fragment2")
    testSelection("root", "subnav")
    testSelection("subnav", "fragment3")

    testSelection("root", "fragment1", "fragment2")
    testSelection("root", "fragment3", "fragment2")
    testSelection("subnav", "fragment3", "action2")
  }

  private fun testSelection(expectedRoot: String, vararg id: String) {
    val component = id.map { model.find(it)!! }
    surface.selectionModel.setSelection(component.toList())

    assertThat(component).containsExactlyElementsIn(selectionModel.currentSelection)

    val expectedNavigation = model.find(expectedRoot)!!
    assertEquals(surface.currentNavigation, expectedNavigation)
  }

  fun testText() {
    testText(model, "root")
    testText(model, "fragment1")
    testText(model, "fragment2")
    testText(model, "subnav")
    testText(model, "action1")
    testText(model, "action2")
  }

  private fun testText(model: NlModel, id: String) {
    val component = model.find(id)!!
    val nodeType = TreePanel.NlComponentNodeType()
    assertEquals(id, nodeType.idOf(component))
    assertNull(nodeType.textValueOf(component))
  }

  fun testScrollToFragment() {
    testScrolling(arrayOf("fragment1"), arrayOf("fragment1"))
  }

  fun testScrollToRoot() {
    testScrolling(arrayOf("root"), arrayOf())
  }

  fun testScrollToMultiple() {
    testScrolling(arrayOf("fragment1", "fragment2", "subnav", "action1"), arrayOf("fragment1", "fragment2", "subnav"))
  }

  private fun testScrolling(selection: Array<String>, expected: Array<String>) {
    val components = selection.map { model.find(it)!! }
    val spy = spy(surface)
    panel.setToolContext(spy)
    spy.selectionModel.setSelection(components)
    panel.updateSelection()
    verify(spy).scrollToCenter(expected.map { model.find(it)!! })
  }
}