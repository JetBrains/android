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
package com.android.tools.idea.naveditor.structure

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LABEL
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.google.common.collect.ImmutableList
import com.intellij.ui.ColoredListCellRenderer
import icons.StudioIcons
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon

/**
 * Tests for [DestinationList]
 */
class DestinationListTest : NavTestCase() {

  private var _model: SyncNlModel? = null
  private var _list: DestinationList? = null

  private val model: SyncNlModel
    get() = _model!!
  private val list: DestinationList
    get() = _list!!

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    _model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent("fragment1"),
            fragmentComponent("fragment2"),
            navigationComponent("subnav")
                .unboundedChildren(fragmentComponent("fragment3"))))
        .build()
    val def = DestinationList.DestinationListDefinition()
    _list = def.factory.create() as DestinationList
    val surface = model.surface
    val sceneView = NavView(surface as NavDesignSurface, surface.sceneManager!!)
    `when`<SceneView>(surface.getCurrentSceneView()).thenReturn(sceneView)
    list.setToolContext(surface)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      _model = null
      _list = null
    }
    finally {
      super.tearDown()
    }
  }

  fun testSelection() {
    val def = DestinationList.DestinationListDefinition()
    val list = def.factory.create() as DestinationList
    list.setToolContext(model.surface)
    var selection = ImmutableList.of(model.find("fragment1")!!)
    val modelSelectionModel = model.surface.selectionModel
    modelSelectionModel.setSelection(selection)
    val listSelectionModel = list.mySelectionModel
    assertEquals(selection, listSelectionModel.selection)

    selection = ImmutableList.of(model.find("fragment2")!!)
    modelSelectionModel.setSelection(selection)
    assertEquals(selection, listSelectionModel.selection)

    selection = ImmutableList.of(model.find("fragment1")!!, model.find("fragment2")!!)
    modelSelectionModel.setSelection(selection)
    assertEquals(selection, listSelectionModel.selection)

    selection = ImmutableList.of()
    modelSelectionModel.setSelection(selection)
    assertEquals(selection, listSelectionModel.selection)

    selection = ImmutableList.of(model.find("fragment1")!!)
    listSelectionModel.setSelection(selection)
    assertEquals(selection, modelSelectionModel.selection)

    selection = ImmutableList.of(model.find("fragment2")!!)
    listSelectionModel.setSelection(selection)
    assertEquals(selection, modelSelectionModel.selection)

    selection = ImmutableList.of(model.find("fragment1")!!, model.find("fragment2")!!)
    listSelectionModel.setSelection(selection)
    assertEquals(selection, modelSelectionModel.selection)
  }

  fun testSubflow() {
    val def = DestinationList.DestinationListDefinition()
    val list = def.factory.create() as DestinationList
    list.setToolContext(model.surface)
    var selection = ImmutableList.of(model.find("subnav")!!)
    val modelSelectionModel = model.surface.selectionModel
    modelSelectionModel.setSelection(selection)
    val listSelectionModel = list.mySelectionModel
    assertEquals(selection, listSelectionModel.selection)

    selection = ImmutableList.of(model.find("subnav")!!)
    listSelectionModel.setSelection(selection)
    assertEquals(selection, modelSelectionModel.selection)
  }

  fun testModifyModel() {
    val root = rootComponent("root").unboundedChildren(
        fragmentComponent("fragment1"),
        fragmentComponent("fragment2"))
    val modelBuilder = model("nav.xml", root)
    val model = modelBuilder.build()
    val def = DestinationList.DestinationListDefinition()
    val list = def.factory.create() as DestinationList

    val sceneView = NavView(model.surface as NavDesignSurface, model.surface.sceneManager!!)
    `when`<SceneView>(model.surface.currentSceneView).thenReturn(sceneView)
    list.setToolContext(model.surface)


    assertEquals(ImmutableList.of(model.find("fragment1")!!, model.find("fragment2")!!), Collections.list(list.myListModel.elements()))


    root.addChild(fragmentComponent("fragment3"), null)
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)


    assertEquals(ImmutableList.of(model.find("fragment1")!!, model.find("fragment2")!!, model.find("fragment3")!!),
        Collections.list(list.myListModel.elements()))

    // Verify that modifications that don't add or remove components don't cause the selection to change
    val fragment3 = ImmutableList.of(model.find("fragment3")!!)
    model.surface.selectionModel.setSelection(fragment3)
    assertEquals(fragment3, list.mySelectionModel.selection)

    model.notifyModified(NlModel.ChangeType.EDIT)
    assertEquals(fragment3, list.mySelectionModel.selection)
  }

  fun testDoubleClickActivity() {
    val nlComponent = model.find("fragment2")!!
    model.surface.selectionModel.setSelection(ImmutableList.of(nlComponent))
    list.myList.dispatchEvent(MouseEvent(list.myList, MouseEvent.MOUSE_CLICKED, 1, 0, 0, 0, 2, false))
    verify(model.surface as NavDesignSurface).notifyComponentActivate(nlComponent)
  }

  fun testBack() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            navigationComponent("subnav")
                .withLabelAttribute("sub nav")
                .unboundedChildren(navigationComponent("subsubnav")
                    .withLabelAttribute("sub sub nav"))))
        .build()

    val def = DestinationList.DestinationListDefinition()
    val list = def.factory.create() as DestinationList
    val surface = model.surface as NavDesignSurface
    val sceneView = NavView(surface, surface.sceneManager!!)
    `when`<SceneView>(surface.currentSceneView).thenReturn(sceneView)
    list.setToolContext(surface)

    var root: NlComponent = model.components[0]!!
    `when`(surface.currentNavigation).thenReturn(root)
    surface.selectionModel.setSelection(ImmutableList.of(root))
    surface.selectionModel.clear()

    assertFalse(list.myBackPanel.isVisible)

    root = root.getChild(0)!!
    `when`(surface.currentNavigation).thenReturn(root)
    surface.selectionModel.setSelection(ImmutableList.of(root))
    surface.selectionModel.clear()

    assertTrue(list.myBackPanel.isVisible)
    assertEquals(DestinationList.ROOT_NAME, list.myBackLabel.text)

    list.goBack()
    verify(surface).currentNavigation = root.parent!!

    root = root.getChild(0)!!
    `when`(surface.currentNavigation).thenReturn(root)
    surface.selectionModel.setSelection(ImmutableList.of(root))
    surface.selectionModel.clear()

    assertTrue(list.myBackPanel.isVisible)
    assertEquals("sub nav", list.myBackLabel.text)
  }

  fun testRendering() {
    val model = model("nav.xml",
        rootComponent("root").withStartDestinationAttribute("fragment2")
            .unboundedChildren(
                fragmentComponent("fragment1").withAttribute(ANDROID_URI, ATTR_LABEL, "fragmentLabel"),
                fragmentComponent("fragment2"),
                activityComponent("activity"),
                navigationComponent("nav1").withAttribute(ANDROID_URI, ATTR_LABEL, "navName"),
                navigationComponent("nav2"),
                includeComponent("navigation")))
        .build()
    val def = DestinationList.DestinationListDefinition()
    val list = def.factory.create() as DestinationList
    val surface = model.surface
    val sceneView = NavView(surface as NavDesignSurface, surface.sceneManager!!)
    `when`<SceneView>(surface.getCurrentSceneView()).thenReturn(sceneView)
    list.setToolContext(surface)

    assertEquals(6, list.myList.itemsCount)

    val renderer = list.myList.cellRenderer
    val result = HashMap<String, Icon>()
    for (i in 0 until list.myList.itemsCount) {
      val component = renderer.getListCellRendererComponent(list.myList, list.myList.model.getElementAt(i), i, false, false)
          as ColoredListCellRenderer<*>
      result.put(component.toString(), component.icon)
    }

    assertEquals(StudioIcons.NavEditor.Tree.FRAGMENT, result["fragmentLabel"])
    assertEquals(StudioIcons.NavEditor.Tree.FRAGMENT, result["fragment2 - Start"])
    assertEquals(StudioIcons.NavEditor.Tree.ACTIVITY, result["activity"])
    assertEquals(StudioIcons.NavEditor.Tree.NESTED_GRAPH, result["navName"])
    assertEquals(StudioIcons.NavEditor.Tree.NESTED_GRAPH, result["navName"])
    assertEquals(StudioIcons.NavEditor.Tree.INCLUDE_GRAPH, result["myCoolLabel"])
  }
}
