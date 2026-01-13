/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DeviceDisplayListener
import com.android.tools.idea.streaming.core.DisplayOwner
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import javax.swing.JPanel
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TabComponentsTest {

  @get:Rule val displayViewRule = EmulatorViewRule()

  private lateinit var displayListeners: MutableList<DeviceDisplayListener>
  private lateinit var displayView1: AbstractDisplayView
  private lateinit var displayView2: AbstractDisplayView

  @Before
  fun setUp() {
    displayListeners = mutableListOf()
    displayView1 = displayViewRule.newEmulatorView()
    displayView2 = displayViewRule.newEmulatorView()
  }

  @Test
  fun testInitialDisplaysAreAdded() {
    val tabComponents = createTabComponents()
    assertThat(tabComponents.displayList.value).hasSize(2)
  }

  @Test
  fun testDynamicDisplaysAreHandled() {
    val tabComponents = createTabComponents()
    assertThat(tabComponents.displayList.value).hasSize(2)

    assertThat(displayListeners).hasSize(1)
    displayListeners.first().displayRemoved(displayView2)

    assertThat(tabComponents.displayList.value).containsExactly(displayView1)

    val newDisplay = displayViewRule.newEmulatorView()
    displayListeners.first().displayAdded(newDisplay)

    assertThat(tabComponents.displayList.value).containsExactly(displayView1, newDisplay)
  }

  @Test
  fun testListenerIsRemovedOnDispose() {
    val tabComponents = createTabComponents()
    assertThat(displayListeners).hasSize(1)
    Disposer.dispose(tabComponents)
    assertThat(displayListeners).hasSize(0)
  }

  private fun createTabComponents(): TabComponents {
    val container = JPanel()
    val content = JPanel()
    container.add(content)

    content.add(displayView1)
    content.add(displayView2)

    return TabComponents(
      disposable = displayViewRule.disposable,
      tabContentPanel = content,
      displayOwner =
        object : DisplayOwner {
          override fun addDeviceDisplayListener(listener: DeviceDisplayListener) {
            displayListeners.add(listener)
          }

          override fun removeDeviceDisplayListener(listener: DeviceDisplayListener) {
            displayListeners.remove(listener)
          }
        },
    )
  }
}
