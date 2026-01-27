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
package com.android.tools.idea.actions

import com.android.resources.NightMode
import com.android.tools.configurations.Wallpaper
import com.android.tools.idea.configurations.ConfigurationManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import javax.swing.JMenuItem
import javax.swing.MenuSelectionManager
import junit.framework.Assert
import org.jetbrains.android.AndroidTestCase
import javax.swing.MenuElement

class SystemUiModeActionTest : AndroidTestCase() {

  /** Tests the basic actions of [SystemUiModeAction] for setting night mode and wallpaper. */
  fun testActions() {
    val file = myFixture.copyFileToProject("configurations/layout1.xml", "res/layout/layout1.xml")
    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    val configuration = manager.getConfiguration(file)
    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(configuration))
    val systemUiModeAction = SystemUiModeAction()

    val wallpaperActions = systemUiModeAction.getWallpaperActions()
    val wallpapers = enumValues<Wallpaper>()
    Assert.assertEquals(wallpapers.size + 1, wallpaperActions.size)
    wallpaperActions.forEachIndexed { index, action ->
      runInEdtAndWait { action.actionPerformed(TestActionEvent.createTestEvent(dataContext)) }
      if (index < wallpapers.size) {
        Assert.assertEquals(wallpapers[index].resourcePath, configuration.wallpaperPath)
      } else {
        Assert.assertEquals(null, configuration.wallpaperPath)
      }
    }

    val nightModeActions = systemUiModeAction.getNightModeActions()
    val nightModes = enumValues<NightMode>()
    nightModeActions.forEachIndexed { index, action ->
      runInEdtAndWait { action.actionPerformed(TestActionEvent.createTestEvent(dataContext)) }
      Assert.assertEquals(nightModes[index], configuration.nightMode)
    }
  }

  /**
   * Verifies the horizontal navigation logic for dynamic color (wallpaper) items.
   *
   * This unit test directly calls [handleNavigation] to ensure that selecting adjacent wallpaper
   * items via keyboard correctly updates the [MenuSelectionManager] state.
   */
  fun testHandleNavigation() {
    val file = myFixture.copyFileToProject("configurations/layout1.xml", "res/layout/layout1.xml")
    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    val configuration = manager.getConfiguration(file)
    val dataContext =
      SimpleDataContext.builder()
        .add(CONFIGURATIONS, listOf(configuration))
        .add(CommonDataKeys.PROJECT, project)
        .build()
    val systemUiModeAction = SystemUiModeAction()

    runInEdtAndWait {
      val menu = systemUiModeAction.createPopupMenu(dataContext)
      val wallpaperItems = mutableListOf<JMenuItem>()
      // Identify wallpaper items by their class name since the class is private.
      for (i in 0 until menu.componentCount) {
        val component = menu.getComponent(i)
        if (component.javaClass.name.endsWith("WallpaperItem")) {
          wallpaperItems.add(component as JMenuItem)
        }
      }

      assertTrue(
        "Should have at least 2 wallpaper items for navigation test",
        wallpaperItems.size >= 2,
      )

      val selectionManager = MenuSelectionManager.defaultManager()

      // 1. Simulate selecting the first wallpaper item.
      selectionManager.selectedPath = arrayOf<MenuElement>(menu, wallpaperItems[0])

      // 2. Test horizontal navigation to the right.
      val handledRight = handleNavigation(menu, NavigationDirection.RIGHT, selectionManager)
      assertTrue("Navigation right should be handled", handledRight)
      assertEquals(
        "Selection should move to the second item",
        wallpaperItems[1],
        selectionManager.selectedPath.last(),
      )

      // 3. Test horizontal navigation back to the left.
      val handledLeft = handleNavigation(menu, NavigationDirection.LEFT, selectionManager)
      assertTrue("Navigation left should be handled", handledLeft)
      assertEquals(
        "Selection should move back to the first item",
        wallpaperItems[0],
        selectionManager.selectedPath.last(),
      )

      // 4. Test boundary condition: navigating left from the first item should not be handled.
      val handledLeftBoundary = handleNavigation(menu, NavigationDirection.LEFT, selectionManager)
      assertFalse("Navigation left from first item should not be handled", handledLeftBoundary)
      assertEquals(
        "Selection should remain on the first item",
        wallpaperItems[0],
        selectionManager.selectedPath.last(),
      )

      selectionManager.clearSelectedPath()
    }
  }
}
