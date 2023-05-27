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
package com.android.tools.idea.ui.resourcemanager.actions

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.RESOURCE_DESIGN_ASSETS_KEY
import com.google.common.truth.Truth
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.testFramework.MapDataContext
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshDesignAssetActionTest {

  private lateinit var actionManager: ActionManager

  @Before
  fun setUp() {
    actionManager = Mockito.mock(ActionManagerEx::class.java)
  }

  @Test
  fun testEnabled() {
    val latch = CountDownLatch(1)
    val refreshAction = RefreshDesignAssetAction { latch.countDown() }
    val assets = getDesignAssets(arrayOf(ResourceType.DRAWABLE, ResourceType.MIPMAP, ResourceType.MENU, ResourceType.LAYOUT))
    val dataContext = MapDataContext().apply { put(RESOURCE_DESIGN_ASSETS_KEY.name, assets) }
    val actionEvent = AnActionEvent(null, dataContext, "ActionTest", Presentation(), actionManager, 0)
    refreshAction.update(actionEvent)
    assertTrue(actionEvent.presentation.isEnabledAndVisible)
    refreshAction.actionPerformed(actionEvent)
    Truth.assertThat(latch.count).isEqualTo(0)
  }

  @Test
  fun testDisabledWithColorDesignAsset() {
    testDisabled(getDesignAssets(arrayOf(ResourceType.DRAWABLE, ResourceType.LAYOUT, ResourceType.COLOR)))
  }

  @Test
  fun testDisabledWithNoDesignAssets() {
    testDisabled(emptyArray())
  }

  private fun testDisabled(assets: Array<DesignAsset>) {
    val latch = CountDownLatch(1)
    val refreshAction = RefreshDesignAssetAction { latch.countDown() }
    val dataContext = MapDataContext().apply { put(RESOURCE_DESIGN_ASSETS_KEY.name, assets) }
    val actionEvent = AnActionEvent(null, dataContext, "ActionTest", Presentation(), actionManager, 0)
    refreshAction.update(actionEvent)
    assertFalse(actionEvent.presentation.isEnabledAndVisible)
    refreshAction.actionPerformed(actionEvent)
    Truth.assertThat(latch.count).isEqualTo(1) // Not called when disabled.
  }

  /**
   * Returns simple [DesignAsset]s for each of the given [ResourceType]s.
   */
  private fun getDesignAssets(types: Array<ResourceType>): Array<DesignAsset> {
    return types.map { type ->
      val name = type.getName()
      DesignAsset(MockVirtualFile("$name.xml"), emptyList(), type, "my_$name")
    }.toTypedArray()
  }
}