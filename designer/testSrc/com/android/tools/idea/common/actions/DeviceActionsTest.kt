/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.common.actions

import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.DeviceMenuAction
import com.android.tools.idea.configurations.DeviceMenuAction2
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.ApplicationRule
import junit.framework.Assert.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test

class NextDeviceActionTest {

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withAndroidModel().onEdt()


  @Test
  fun testNextDeviceAction() {
    val nextDeviceAction = NextDeviceAction.getInstance()

    val file = projectRule.fixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)

    val configManager = ConfigurationManager.getOrCreateInstance(projectRule.fixture.module)
    val config = configManager.getConfiguration(file.virtualFile)

    val setDeviceActions = getSetDeviceActions(config)

    val firstDevice = setDeviceActions.first().device
    config.setDevice(firstDevice, false)
    var index = 1
    while (index < setDeviceActions.size) {
      nextDeviceAction.switchDevice(config)
      assertEquals(setDeviceActions[index].device, config.device)
      index += 1
    }
    // Applying NextDeviceAction to last device will move to the first device.
    nextDeviceAction.switchDevice(config)
    assertEquals(firstDevice, config.device)
  }
}

class PreviousDeviceActionTest {

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withAndroidModel().onEdt()


  @Test
  fun testPreviousDeviceAction() {
    val previousDeviceAction = PreviousDeviceAction.getInstance()

    val file = projectRule.fixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)

    val configManager = ConfigurationManager.getOrCreateInstance(projectRule.fixture.module)
    val config = configManager.getConfiguration(file.virtualFile)

    val setDeviceActions = getSetDeviceActions(config)

    val lastDevice = setDeviceActions.last().device
    config.setDevice(lastDevice, false)
    var index = setDeviceActions.lastIndex - 1
    while (index >= 0) {
      previousDeviceAction.switchDevice(config)
      assertEquals(setDeviceActions[index].device, config.device)
      index -= 1
    }
    // Applying PreviousDeviceAction to first device will move to the last device.
    previousDeviceAction.switchDevice(config)
    assertEquals(lastDevice, config.device)
  }
}

private fun getSetDeviceActions(config: Configuration): List<DeviceMenuAction.SetDeviceAction> {
  val menuAction = if (StudioFlags.NELE_NEW_DEVICE_MENU.get()) {
    DeviceMenuAction2 { config }.apply { updateActions(DataContext.EMPTY_CONTEXT) }
  }
  else {
    DeviceMenuAction { config }.apply { updateActions(DataContext.EMPTY_CONTEXT) }
  }

  return menuAction.getChildren(null)
    .map { if (it is ActionGroup) it.getChildren(null) else arrayOf(it) }
    .flatMap { it.toList() }
    .filterIsInstance<DeviceMenuAction.SetDeviceAction>()
}

@Language("XML")
private const val LAYOUT_FILE_CONTENT = """
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
</LinearLayout>
"""
