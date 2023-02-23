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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.actions.GotoComponentAction
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.actions.ActivateComponentAction
import com.android.tools.idea.naveditor.actions.AddGlobalAction
import com.android.tools.idea.naveditor.actions.AddToExistingGraphAction
import com.android.tools.idea.naveditor.actions.AddToNewGraphAction
import com.android.tools.idea.naveditor.actions.AutoArrangeAction
import com.android.tools.idea.naveditor.actions.EditExistingAction
import com.android.tools.idea.naveditor.actions.ReturnToSourceAction
import com.android.tools.idea.naveditor.actions.ScrollToDestinationAction
import com.android.tools.idea.naveditor.actions.StartDestinationAction
import com.android.tools.idea.naveditor.actions.ToDestinationAction
import com.android.tools.idea.naveditor.actions.ToSelfAction
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.uibuilder.actions.SelectAllAction
import com.intellij.ide.actions.CopyAction
import com.intellij.ide.actions.CutAction
import com.intellij.ide.actions.DeleteAction
import com.intellij.ide.actions.PasteAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys.COPY_PROVIDER
import com.intellij.openapi.actionSystem.PlatformDataKeys.CUT_PROVIDER
import com.intellij.openapi.actionSystem.PlatformDataKeys.DELETE_ELEMENT_PROVIDER
import com.intellij.openapi.actionSystem.PlatformDataKeys.PASTE_PROVIDER
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.android.resourceManagers.LocalResourceManager

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
    val resourceFiles =
        LocalResourceManager.getInstance(myFacet.module)!!.findResourceFiles(ResourceNamespace.TODO(), ResourceFolderType.LAYOUT)
    val layout = resourceFiles.stream().filter { file -> file.name == "activity_main.xml" }.findFirst().get() as XmlFile
    WriteCommandAction.runWriteCommandAction(project) {
      val destinationClass = JavaPsiFacade.getInstance(project).findClass("mytest.navtest.MainActivity",
                                                                          GlobalSearchScope.allScope(project))!!
      Destination.RegularDestination(surface.currentNavigation, "activity", null, destinationClass, "myId", layout)
        .addToGraph()
    }
    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<fragment>, instance=1}\n" +
                 "    NlComponent{tag=<navigation>, instance=2}\n" +
                 "        NlComponent{tag=<fragment>, instance=3}\n" +
                 "    NlComponent{tag=<activity>, instance=4}",
                 NlTreeDumper().toTree(model.components))
    val newChild = model.find("myId")!!
    assertEquals(SdkConstants.LAYOUT_RESOURCE_PREFIX + "activity_main",
                 newChild.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT))
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
    whenever(surface.currentNavigation).thenReturn(model.find("subflow"))

    val psiClass = JavaPsiFacade.getInstance(project).findClass("mytest.navtest.MainActivity", GlobalSearchScope.allScope(project))
    WriteCommandAction.runWriteCommandAction(project) {
      Destination.RegularDestination(surface.currentNavigation, "activity", null, psiClass!!).addToGraph()
    }
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
    val fragment1 = model.find("fragment1")!!
    val actionManager = NavActionManager(surface)
    surface.selectionModel.setSelection(listOf(fragment1))
    var menuGroup = actionManager.getPopupMenuActions(fragment1)

    var items = menuGroup.getChildren(null)

    assertEquals(13, items.size)
    validateItem(items[0], ActivateComponentAction::class.java, "Edit", false)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], ActionGroup::class.java, "Add Action", true)
    validateItem(items[3], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", true)
    validateItem(items[5], ScrollToDestinationAction::class.java, "Scroll into view", true)
    validateItem(items[6], Separator::class.java, null, true)
    validateItem(items[7], CutAction::class.java, "Cut", true)
    validateItem(items[8], CopyAction::class.java, "Copy", true)
    validateItem(items[10], DeleteAction::class.java, "Delete", true)
    validateItem(items[11], Separator::class.java, null, true)
    validateItem(items[12], GotoComponentAction::class.java, "Go to XML", true)

    // Make a copy to ensure Paste is available
    surface.actionHandlerProvider.apply(surface).performCopy(DataContext.EMPTY_CONTEXT)
    validateItem(items[9], PasteAction::class.java, "Paste", true)

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

    val fragment2 = model.find("fragment2")!!
    surface.selectionModel.setSelection(listOf(fragment2))
    menuGroup = actionManager.getPopupMenuActions(fragment2)
    items = menuGroup.getChildren(null)
    validateItem(items[0], ActivateComponentAction::class.java, "Edit", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", false)

    val fragment3 = model.find("fragment3")!!
    menuGroup = actionManager.getPopupMenuActions(fragment3)
    surface.selectionModel.setSelection(listOf(fragment3))
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
    val activity = model.find("activity")!!
    // Select the activity to enable Cut and Copy
    surface.selectionModel.setSelection(listOf(activity))
    val actionManager = NavActionManager(surface)
    val menuGroup = actionManager.getPopupMenuActions(activity)

    val items = menuGroup.getChildren(null)

    assertEquals(13, items.size)
    validateItem(items[0], ActivateComponentAction::class.java, "Edit", false)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], ActionGroup::class.java, "Add Action", false)
    validateItem(items[3], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", false)
    validateItem(items[5], ScrollToDestinationAction::class.java, "Scroll into view", true)
    validateItem(items[6], Separator::class.java, null, true)
    validateItem(items[7], CutAction::class.java, "Cut", true)
    validateItem(items[8], CopyAction::class.java, "Copy", true)
    validateItem(items[10], DeleteAction::class.java, "Delete", true)
    validateItem(items[11], Separator::class.java, null, true)
    validateItem(items[12], GotoComponentAction::class.java, "Go to XML", true)

    // Make a copy to ensure Paste is available
    surface.actionHandlerProvider.apply(surface).performCopy(DataContext.EMPTY_CONTEXT)
    validateItem(items[9], PasteAction::class.java, "Paste", true)

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
    val subflow = model.find("subflow")!!
    val actionManager = NavActionManager(surface)
    val menuGroup = actionManager.getPopupMenuActions(subflow)
    surface.selectionModel.setSelection(listOf(subflow))

    val items = menuGroup.getChildren(null)

    assertEquals(13, items.size)
    validateItem(items[0], ActivateComponentAction::class.java, "Open", true)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], ActionGroup::class.java, "Add Action", true)
    validateItem(items[3], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", false)
    validateItem(items[5], ScrollToDestinationAction::class.java, "Scroll into view", true)
    validateItem(items[6], Separator::class.java, null, true)
    validateItem(items[7], CutAction::class.java, "Cut", true)
    validateItem(items[8], CopyAction::class.java, "Copy", true)
    validateItem(items[10], DeleteAction::class.java, "Delete", true)
    validateItem(items[11], Separator::class.java, null, true)
    validateItem(items[12], GotoComponentAction::class.java, "Go to XML", true)

    // Make a copy to ensure Paste is available
    surface.actionHandlerProvider.apply(surface).performCopy(DataContext.EMPTY_CONTEXT)
    validateItem(items[9], PasteAction::class.java, "Paste", true)

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
    val nav = model.find("nav")!!
    val actionManager = NavActionManager(surface)
    val menuGroup = actionManager.getPopupMenuActions(nav)
    surface.selectionModel.setSelection(listOf(nav))

    val items = menuGroup.getChildren(null)

    assertEquals(13, items.size)
    validateItem(items[0], ActivateComponentAction::class.java, "Open", true)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], ActionGroup::class.java, "Add Action", false)
    validateItem(items[3], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[4], StartDestinationAction::class.java, "Set as Start Destination", true)
    validateItem(items[5], ScrollToDestinationAction::class.java, "Scroll into view", true)
    validateItem(items[6], Separator::class.java, null, true)
    validateItem(items[7], CutAction::class.java, "Cut", true)
    validateItem(items[8], CopyAction::class.java, "Copy", true)
    validateItem(items[10], DeleteAction::class.java, "Delete", true)
    validateItem(items[11], Separator::class.java, null, true)
    validateItem(items[12], GotoComponentAction::class.java, "Go to XML", true)

    // Make a copy to ensure Paste is available
    surface.actionHandlerProvider.apply(surface).performCopy(DataContext.EMPTY_CONTEXT)
    validateItem(items[9], PasteAction::class.java, "Paste", true)

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
    val menuGroup = actionManager.getPopupMenuActions(subnav)
    surface.selectionModel.setSelection(listOf(subnav))

    val items = menuGroup.getChildren(null)

    assertEquals(9, items.size)
    validateItem(items[0], SelectAllAction::class.java, "Select All", true)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], AutoArrangeAction::class.java, "Auto Arrange", true)
    validateItem(items[3], Separator::class.java, null, true)
    validateItem(items[4], ZoomInAction::class.java, "Zoom In", true)
    validateItem(items[5], ZoomOutAction::class.java, "Zoom Out", false)
    validateItem(items[6], ZoomToFitAction::class.java, "Zoom to Fit Screen", true)
    validateItem(items[7], Separator::class.java, null, true)
    validateItem(items[8], GotoComponentAction::class.java, "Go to XML", true)
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
    val menuGroup = actionManager.getPopupMenuActions(fragment1)
    val items = menuGroup.getChildren(null)

    assertEquals(6, items.size)
    validateItem(items[0], ActionGroup::class.java, "Move to Nested Graph", true)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], CutAction::class.java, "Cut", true)
    validateItem(items[3], CopyAction::class.java, "Copy", true)
    validateItem(items[4], PasteAction::class.java, "Paste", true)
    validateItem(items[5], DeleteAction::class.java, "Delete", true)

    val nestedGraphItems = (items[0] as ActionGroup).getChildren(null)
    assertEquals(1, nestedGraphItems.size)
    validateItem(nestedGraphItems[0], AddToNewGraphAction::class.java, "New Graph", true)

  }

  fun testActionContextMenu() {
    val model = model("nav.xml") {
      navigation(startDestination = "fragment2") {
        fragment("fragment1") {
          action("action1", "fragment2")
        }
        fragment("fragment2")
      }
    }
    surface.model = model
    val actionManager = NavActionManager(surface)
    val action1 = model.find("action1")!!
    val menuGroup = actionManager.getPopupMenuActions(action1)
    surface.selectionModel.setSelection(listOf(action1))
    val items = menuGroup.getChildren(null)

    assertEquals(6, items.size)
    validateItem(items[0], EditExistingAction::class.java, "Edit", true)
    validateItem(items[1], Separator::class.java, null, true)
    validateItem(items[2], CutAction::class.java, "Cut", true)
    validateItem(items[3], CopyAction::class.java, "Copy", true)
    validateItem(items[4], PasteAction::class.java, "Paste", true)
    validateItem(items[5], DeleteAction::class.java, "Delete", true)
  }

  private fun validateItem(item: AnAction, c: Class<*>, name: String?, enabled: Boolean) {
    val surfaceActionProvider = surface.actionHandlerProvider.apply(surface)
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(DESIGN_SURFACE, surface)
      .add(ZOOMABLE_KEY, surface)
      .add(COPY_PROVIDER, surfaceActionProvider)
      .add(CUT_PROVIDER, surfaceActionProvider)
      .add(PASTE_PROVIDER, surfaceActionProvider)
      .add(DELETE_ELEMENT_PROVIDER, surfaceActionProvider)
      .build()
    val event = TestActionEvent(dataContext, item)
    item.update(event)
    assertInstanceOf(item, c)
    assertEquals(name, event.presentation.text)
    if (item is ActionGroup) {
      assertEquals(!enabled,
                   item.disableIfNoVisibleChildren() &&
                   !item.hideIfNoVisibleChildren() &&
                   item.getChildren(null).none { event.presentation.isVisible })
    }
    else {
      assertEquals(enabled, event.presentation.isEnabled)
    }
  }
}