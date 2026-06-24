/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.monitor.ui.menu.item

import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorActionsListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceMonitorMenuItemsTest {

  @Test
  fun testKillMenuItem() {
    val listener = FakeDeviceMonitorActionsListener(numOfSelectedNodes = 1)
    val item = KillMenuItem(listener, MenuContext.Toolbar)

    assertThat(item.text).isEqualTo("Kill process")
    assertThat(item.description).isEqualTo("Terminates the target VM")
  }

  @Test
  fun testKillMenuItemMultipleNodes() {
    val listener = FakeDeviceMonitorActionsListener(numOfSelectedNodes = 3)
    val item = KillMenuItem(listener, MenuContext.Toolbar)

    assertThat(item.text).isEqualTo("Kill processes")
    assertThat(item.description).isEqualTo("Terminates the target VM")
  }

  @Test
  fun testForceStopMenuItem() {
    val listener = FakeDeviceMonitorActionsListener(numOfSelectedNodes = 1)
    val item = ForceStopMenuItem(listener, MenuContext.Toolbar)

    assertThat(item.text).isEqualTo("Force stop process")
    assertThat(item.description).isEqualTo("Executes command: am force-stop")
  }

  @Test
  fun testForceStopMenuItemMultipleNodes() {
    val listener = FakeDeviceMonitorActionsListener(numOfSelectedNodes = 2)
    val item = ForceStopMenuItem(listener, MenuContext.Toolbar)

    assertThat(item.text).isEqualTo("Force stop processes")
    assertThat(item.description).isEqualTo("Executes command: am force-stop")
  }

  @Test
  fun testBackupAndRestoreMenuItems() {
    val listener = FakeDeviceMonitorActionsListener()
    val backup = BackupMenuItem(listener, MenuContext.Toolbar)
    val restore = RestoreMenuItem(listener, MenuContext.Toolbar)

    assertThat(backup.text).isEqualTo("Backup app data")
    assertThat(backup.description).isEqualTo("Backs up app data")

    assertThat(restore.text).isEqualTo("Restore app data")
    assertThat(restore.description).isEqualTo("Restores app from a backup file")
  }

  @Test
  fun testClearAndUninstallMenuItems() {
    val listener = FakeDeviceMonitorActionsListener(numOfSelectedNodes = 1)
    val clear = ClearAppDataMenuItem(listener, MenuContext.Toolbar)
    val uninstall = UninstallAppMenuItem(listener, MenuContext.Toolbar)

    assertThat(clear.text).isEqualTo("Clear app data")
    assertThat(clear.description).isEqualTo("Clears application data")

    assertThat(uninstall.text).isEqualTo("Uninstall app")
    assertThat(uninstall.description).isEqualTo("Uninstalls application")
  }

  @Test
  fun testPackageFilterMenuItem() {
    val listener = FakeDeviceMonitorActionsListener()
    val item = PackageFilterMenuItem(listener)

    item.shouldBeEnabled = false
    item.isActionSelected = false
    assertThat(item.text).isEqualTo("Turn on package filter")
    assertThat(item.description).isEqualTo("Disabled due to no application IDs found")

    item.shouldBeEnabled = true
    item.isActionSelected = true
    assertThat(item.text).isEqualTo("Turn off package filter")
    assertThat(item.description).isNull()
  }

  private class FakeDeviceMonitorActionsListener(
    override val numOfSelectedNodes: Int = 0,
    override val selectedProcessInfo: List<ProcessInfo> = emptyList(),
  ) : DeviceMonitorActionsListener {
    override fun refreshNodes() {}

    override fun killNodes() {}

    override fun forceStopNodes() {}

    override fun debugNodes() {}

    override fun packageFilterToggled(isActive: Boolean) {}

    override fun clearAppData() {}

    override fun uninstallApp() {}

    override fun backupApplication() {}

    override fun restoreApplication() {}
  }
}
