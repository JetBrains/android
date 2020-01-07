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

    surface = NavDesignSurface(project, project)
    surface.model = model

    panel = TreePanel()
    panel.setToolContext(surface)

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
    testSelection("fragment1", "root")
    testSelection("fragment2", "root")
    testSelection("subnav", "root")
    testSelection("fragment3", "subnav")
  }

  private fun testSelection(id: String, root: String) {
    val component = model.find(id)!!
    surface.selectionModel.setSelection(listOf(component))

    assertThat(listOf(component)).containsExactlyElementsIn(selectionModel.selection).inOrder()

    val expectedRoot = model.find(root)!!
    assertEquals(surface.currentNavigation, expectedRoot)
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
}