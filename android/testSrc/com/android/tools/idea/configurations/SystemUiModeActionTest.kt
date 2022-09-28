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
package com.android.tools.idea.configurations

import com.android.resources.NightMode
import com.intellij.testFramework.TestActionEvent
import junit.framework.Assert
import org.jetbrains.android.AndroidTestCase

class SystemUiModeActionTest : AndroidTestCase() {

  fun testActions() {
    val file = myFixture.copyFileToProject("configurations/layout1.xml", "res/layout/layout1.xml")
    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    val configuration = manager.getConfiguration(file)
    val systemUiModeAction = SystemUiModeAction { configuration }

    val wallpaperActions = systemUiModeAction.getWallpaperActions()
    val wallpapers = enumValues<Wallpaper>()
    Assert.assertEquals(wallpapers.size + 1, wallpaperActions.size)
    wallpaperActions.forEachIndexed { index, action ->
      action.actionPerformed(TestActionEvent())
      if (index < wallpapers.size) {
        Assert.assertEquals(wallpapers[index].resourcePath, configuration.wallpaperPath)
      } else {
        Assert.assertEquals(null, configuration.wallpaperPath)
      }
    }

    val nightModeActions = systemUiModeAction.getNightModeActions()
    val nightModes = enumValues<NightMode>()
    nightModeActions.forEachIndexed { index, action ->
      action.actionPerformed(TestActionEvent())
      Assert.assertEquals(nightModes[index], configuration.nightMode)
    }
  }
}