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

import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.laf.HeadlessListUI
import com.android.tools.adtui.workbench.ToolWindowCallback
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.editor.NavActionManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.google.common.collect.ImmutableList
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ColoredListCellRenderer
import icons.StudioIcons
import org.mockito.Mockito.*
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

  override fun setUp() {
    super.setUp()
    _model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
        navigation("subnav") {
          fragment("fragment3")
        }
      }
    }
    val surface = model.surface
    val sceneView = NavView(surface as NavDesignSurface, surface.sceneManager!!)
    `when`<SceneView>(surface.getFocusedSceneView()).thenReturn(sceneView)
    val destinationList = DestinationList(project, surface)
    val myList = destinationList.myList
    myList.setSize(200,500)
    _list = destinationList
  }

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
    lateinit var root: NavModelBuilderUtil.NavigationComponentDescriptor
    val modelBuilder = modelBuilder("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
      }.also { root = it }
    }
    val model = modelBuilder.build()

    val sceneView = NavView(model.surface as NavDesignSurface, model.surface.sceneManager!!)
    `when`<SceneView>(model.surface.focusedSceneView).thenReturn(sceneView)
    val list = DestinationList(project, model.surface as NavDesignSurface)

    assertEquals(ImmutableList.of(model.find("fragment1")!!, model.find("fragment2")!!),
                 Collections.list(list.myUnderlyingModel.elements()))

    root.fragment("fragment3")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)


    assertEquals(ImmutableList.of(model.find("fragment1")!!, model.find("fragment2")!!, model.find("fragment3")!!),
        Collections.list(list.myUnderlyingModel.elements()))

    // Verify that modifications that don't add or remove components don't cause the selection to change
    val fragment3 = ImmutableList.of(model.find("fragment3")!!)
    model.surface.selectionModel.setSelection(fragment3)
    assertEquals(fragment3, list.mySelectionModel.selection)

    model.notifyModified(NlModel.ChangeType.EDIT)
    assertEquals(fragment3, list.mySelectionModel.selection)
  }

  fun testDoubleClickActivity() {
    val nlComponent = model.find("fragment2")!!
    val listModel = list.myList.model
    val point = list.myList.indexToLocation((0 until listModel.size).indexOfFirst { listModel.getElementAt(it) == nlComponent })
    list.myList.dispatchEvent(MouseEvent(list.myList, MouseEvent.MOUSE_CLICKED, 1, 0, point.x, point.y, 2, false))
    verify(model.surface as NavDesignSurface).notifyComponentActivate(nlComponent)
  }

  fun testRightClickActivity() {
    val actionManager = mock(NavActionManager::class.java)
    `when`(model.surface.actionManager).thenReturn(actionManager)
    `when`(actionManager.getPopupMenuActions(any())).thenReturn(DefaultActionGroup())
    // We use any ?: Collections.emptyList() below because any() returns null and Kotlin will
    // complain during the null checking
    `when`(actionManager.getToolbarActions(any(), any() ?: Collections.emptyList())).thenReturn(DefaultActionGroup())
    val nlComponent = model.find("fragment2")!!
    val listModel = list.myList.model
    val point = list.myList.indexToLocation((0 until listModel.size).indexOfFirst { listModel.getElementAt(it) == nlComponent })
    val event = MouseEvent(list.myList, MouseEvent.MOUSE_CLICKED, 1, 0, point.x, point.y, 1, true)
    list.myList.dispatchEvent(event)
    verify(actionManager).getPopupMenuActions(any())
  }

  fun testRendering() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1", label = "fragmentLabel", name = "myClass")
        fragment("fragment2")
        activity("activity", name = "myClass2")
        navigation("nav1", label = "navName")
        navigation("nav2")
        include("navigation")
      }
    }
    val surface = model.surface
    val sceneView = NavView(surface as NavDesignSurface, surface.sceneManager!!)
    `when`<SceneView>(surface.getFocusedSceneView()).thenReturn(sceneView)
    val list = DestinationList(project, surface)

    assertEquals(6, list.myList.itemsCount)

    val renderer = list.myList.cellRenderer
    val result = HashMap<String, Icon>()
    for (i in 0 until list.myList.itemsCount) {
      val component = renderer.getListCellRendererComponent(list.myList, list.myList.model.getElementAt(i), i, false, false)
          as ColoredListCellRenderer<*>
      result[component.toString()] = component.icon
    }

    assertEquals(StudioIcons.NavEditor.Tree.FRAGMENT, result["fragment1"])
    assertEquals(StudioIcons.NavEditor.Tree.PLACEHOLDER, result["fragment2 - Start"])
    assertEquals(StudioIcons.NavEditor.Tree.ACTIVITY, result["activity"])
    assertEquals(StudioIcons.NavEditor.Tree.NESTED_GRAPH, result["nav1"])
    assertEquals(StudioIcons.NavEditor.Tree.NESTED_GRAPH, result["nav2"])
    assertEquals(StudioIcons.NavEditor.Tree.INCLUDE_GRAPH, result["nav"])
  }

  fun testKeyStartsFiltering() {
    var called = false
    list.registerCallbacks(object : ToolWindowCallback {
      override fun startFiltering(initialSearchString: String) {
        called = true
      }
    })

    val ui = FakeUi(list)
    list.myList.ui = HeadlessListUI()
    ui.keyboard.setFocus(list.myList)
    ui.keyboard.type(FakeKeyboard.Key.A)
    assertTrue(called)
  }

  fun testFilter() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1", label = "fragmentLabel")
        fragment("fragment2")
        activity("activity")
        navigation("nav1", label = "navName")
        navigation("nav2")
        include("navigation")
      }
    }
    val surface = model.surface
    val sceneView = NavView(surface as NavDesignSurface, surface.sceneManager!!)
    `when`<SceneView>(surface.getFocusedSceneView()).thenReturn(sceneView)
    val list = DestinationList(project, surface)

    list.setFilter("nav")
    assertEquals(3, list.myList.itemsCount)
  }
}
