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
package com.android.tools.idea.naveditor.editor

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.actions.*
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.actions.*
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.ide.actions.DeleteAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.mockito.Mockito.`when`

/**
 * Tests for [NavActionManager]
 */
class NavActionManagerTest : NavTestCase() {

  private lateinit var model: SyncNlModel
  private lateinit var surface: NavDesignSurface

  override fun setUp() {
    super.setUp()
    model = model("nav.xml") {
      navigation("navigation") {
        fragment("fragment1")
        navigation("subnav") {
          fragment("fragment2")
        }
      }
    }
    surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
  }

  fun testAddElement() {
    val layout = LocalResourceManager.getInstance(myFacet.module)!!.findResourceFiles(
        ResourceFolderType.LAYOUT).stream().filter { file -> file.name == "activity_main.xml" }.findFirst().get() as XmlFile
    Destination.RegularDestination(surface.currentNavigation, "activity", null, "MainActivity", "mytest.navtest.MainActivity",
        "myId", layout)
        .addToGraph()
    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<fragment>, instance=1}\n" +
        "    NlComponent{tag=<navigation>, instance=2}\n" +
        "        NlComponent{tag=<fragment>, instance=3}\n" +
        "    NlComponent{tag=<activity>, instance=4}",
        NlTreeDumper().toTree(model.components))
    val newChild = model.find("myId")!!
    assertEquals(SdkConstants.LAYOUT_RESOURCE_PREFIX + "activity_main", newChild.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT))
    assertEquals("mytest.navtest.MainActivity", newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME))
    assertEquals("@+id/myId", newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID))
  }

  fun testAddElementInSubflow() {
    val model = model("nav.xml") {
      navigation("root") {
        navigation("subflow") {
          fragment("fragment2")
        }
        fragment("fragment1")
      }
    }

    val surface = model.surface as NavDesignSurface
    `when`(surface.currentNavigation).thenReturn(model.find("subflow"))

    val psiClass = JavaPsiFacade.getInstance(project).findClass("mytest.navtest.MainActivity", GlobalSearchScope.allScope(project))
    Destination.RegularDestination(surface.currentNavigation, "activity", null, psiClass!!.name, psiClass.qualifiedName).addToGraph()

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<navigation>, instance=1}\n" +
        "        NlComponent{tag=<fragment>, instance=2}\n" +
        "        NlComponent{tag=<activity>, instance=3}\n" +
        "    NlComponent{tag=<fragment>, instance=4}", NlTreeDumper().toTree(model.components))
  }

  fun testFragmentContextMenu() {
    val model = model("nav.xml") {
      navigation(startDestination = "fragment2") {
        fragment("fragment1")
        fragment("fragment2", layout = "@layout/layout")
        fragment("fragment3", name = "foo.bar.Baz")
        navigation("subflow")
      }
    }

    surface.model = model
    val actionManager = NavActionManager(surface)
    var menuGroup = actionManager.createPopupMenu(com.intellij.openapi.actionSystem.ActionManager.getInstance(), model.find("fragment1"))

    var items = menuGroup.getChildren(null)

    assertEquals(9, items.size)
    validateItem(items[0], ActivateComponentAction::class.java, "Edit", false)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], ActionGroup::class.java, "Add Action", true)
    validateItem(items[3], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", true)
    validateItem(items[5], Separator::class.java, null, true)
    validateItem(items[6], DeleteAction::class.java, "Delete", true)
    validateItem(items[7], Separator::class.java, null, true)
    validateItem(items[8], GotoComponentAction::class.java, "Go to XML", true)

    val addActionItems = (items[2] as ActionGroup).getChildren(null)
    assertEquals(4, addActionItems.size)
    validateItem(addActionItems[0], ToDestinationAction::class.java, "To Destination...", true)
    validateItem(addActionItems[1], ToSelfAction::class.java, "To Self", true)
    validateItem(addActionItems[2], ReturnToSourceAction::class.java, "Return to Source", true)
    validateItem(addActionItems[3], AddGlobalAction::class.java, "Global", true)

    val nestedGraphItems = (items[3] as ActionGroup).getChildren(null)
    assertEquals(3, nestedGraphItems.size)
    validateItem(nestedGraphItems[0], AddToNewGraphAction::class.java, "New Graph", true)
    validateItem(nestedGraphItems[1], Separator::class.java, null, true)
    validateItem(nestedGraphItems[2], AddToExistingGraphAction::class.java, "subflow", true)

    menuGroup = actionManager.createPopupMenu(com.intellij.openapi.actionSystem.ActionManager.getInstance(), model.find("fragment2"))
    items = menuGroup.getChildren(null)
    validateItem(items[0], ActivateComponentAction::class.java, "Edit", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", false)

    menuGroup = actionManager.createPopupMenu(com.intellij.openapi.actionSystem.ActionManager.getInstance(), model.find("fragment3"))
    items = menuGroup.getChildren(null)
    validateItem(items[0], ActivateComponentAction::class.java, "Edit", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", true)
  }

  fun testActivityContextMenu() {
    val model = model("nav.xml") {
      navigation(startDestination = "action") {
        activity("activity")
      }
    }

    surface.model = model
    val actionManager = NavActionManager(surface)
    val menuGroup = actionManager.createPopupMenu(com.intellij.openapi.actionSystem.ActionManager.getInstance(), model.find("activity"))

    val items = menuGroup.getChildren(null)

    assertEquals(9, items.size)
    validateItem(items[0], ActivateComponentAction::class.java, "Edit", false)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], ActionGroup::class.java, "Add Action", false)
    validateItem(items[3], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", false)
    validateItem(items[5], Separator::class.java, null, true)
    validateItem(items[6], DeleteAction::class.java, "Delete", true)
    validateItem(items[7], Separator::class.java, null, true)
    validateItem(items[8], GotoComponentAction::class.java, "Go to XML", true)

    val nestedGraphItems = (items[3] as ActionGroup).getChildren(null)
    assertEquals(1, nestedGraphItems.size)
    validateItem(nestedGraphItems[0], AddToNewGraphAction::class.java, "New Graph", true)
  }

  fun testSubnavContextMenu() {
    val model = model("nav.xml") {
      navigation(startDestination = "subflow") {
        navigation("subflow")
      }
    }

    surface.model = model
    val actionManager = NavActionManager(surface)
    val menuGroup = actionManager.createPopupMenu(com.intellij.openapi.actionSystem.ActionManager.getInstance(), model.find("subflow"))

    val items = menuGroup.getChildren(null)

    assertEquals(9, items.size)
    validateItem(items[0], ActivateComponentAction::class.java, "Open", true)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], ActionGroup::class.java, "Add Action", true)
    validateItem(items[3], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", false)
    validateItem(items[5], Separator::class.java, null, true)
    validateItem(items[6], DeleteAction::class.java, "Delete", true)
    validateItem(items[7], Separator::class.java, null, true)
    validateItem(items[8], GotoComponentAction::class.java, "Go to XML", true)

    val addActionItems = (items[2] as ActionGroup).getChildren(null)
    assertEquals(4, addActionItems.size)
    validateItem(addActionItems[0], ToDestinationAction::class.java, "To Destination...", true)
    validateItem(addActionItems[1], ToSelfAction::class.java, "To Self", true)
    validateItem(addActionItems[2], ReturnToSourceAction::class.java, "Return to Source", true)
    validateItem(addActionItems[3], AddGlobalAction::class.java, "Global", true)

    val nestedGraphItems = (items[3] as ActionGroup).getChildren(null)
    assertEquals(1, nestedGraphItems.size)
    validateItem(nestedGraphItems[0], AddToNewGraphAction::class.java, "New Graph", true)
  }

  fun testIncludeContextMenu() {
    val model = model("nav.xml") {
      navigation {
        include("navigation")
      }
    }

    surface.model = model
    val actionManager = NavActionManager(surface)
    val menuGroup = actionManager.createPopupMenu(com.intellij.openapi.actionSystem.ActionManager.getInstance(), model.find("nav"))

    val items = menuGroup.getChildren(null)

    assertEquals(9, items.size)
    validateItem(items[0], ActivateComponentAction::class.java, "Open", true)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], ActionGroup::class.java, "Add Action", false)
    validateItem(items[3], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", true)
    validateItem(items[5], Separator::class.java, null, true)
    validateItem(items[6], DeleteAction::class.java, "Delete", true)
    validateItem(items[7], Separator::class.java, null, true)
    validateItem(items[8], GotoComponentAction::class.java, "Go to XML", true)

    val nestedGraphItems = (items[3] as ActionGroup).getChildren(null)
    assertEquals(1, nestedGraphItems.size)
    validateItem(nestedGraphItems[0], AddToNewGraphAction::class.java, "New Graph", true)
  }

  fun testRootContextMenu() {
    val model = model("nav.xml") {
      navigation {
        navigation("subnav") {
          fragment("fragment")
        }
      }
    }
    surface.model = model
    val subnav = model.find("subnav")!!
    surface.currentNavigation = subnav
    val actionManager = NavActionManager(surface)
    val menuGroup = actionManager.createPopupMenu(com.intellij.openapi.actionSystem.ActionManager.getInstance(), subnav)

    val items = menuGroup.getChildren(null)

    assertEquals(5, items.size)
    validateItem(items[0], ZoomInAction::class.java, "Zoom In", true)
    validateItem(items[1], ZoomOutAction::class.java, "Zoom Out", true)
    validateItem(items[2], ZoomToFitAction::class.java, "Zoom to Fit Screen", true)
    validateItem(items[3], Separator::class.java, null, true)
    validateItem(items[4], GotoComponentAction::class.java, "Go to XML", true)
  }

  fun testMultiSelectContextMenu() {
    val model = model("nav.xml") {
      navigation(startDestination = "fragment2") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    surface.model = model
    val actionManager = NavActionManager(surface)
    val fragment1 = model.find("fragment1")!!
    surface.selectionModel.setSelection(listOf(fragment1, model.find("fragment2")!!))
    val menuGroup = actionManager.createPopupMenu(com.intellij.openapi.actionSystem.ActionManager.getInstance(), fragment1)
    val items = menuGroup.getChildren(null)

    assertEquals(3, items.size)
    validateItem(items[0], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], DeleteAction::class.java, "Delete", true)

    val nestedGraphItems = (items[0] as ActionGroup).getChildren(null)
    assertEquals(1, nestedGraphItems.size)
    validateItem(nestedGraphItems[0], AddToNewGraphAction::class.java, "New Graph", true)

  }

  private fun validateItem(item: AnAction, c: Class<*>, name: String?, enabled: Boolean) {
    assertInstanceOf(item, c)
    assertEquals(name, item.templatePresentation.text)
    if (item is ActionGroup) {
      assertEquals(!enabled,
                   item.disableIfNoVisibleChildren() &&
                   !item.hideIfNoVisibleChildren() &&
                   item.getChildren(null).none { it.templatePresentation.isVisible })
    } else {
      assertEquals(enabled, item.templatePresentation.isEnabled)
    }
  }
}
