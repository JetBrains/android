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
package com.android.tools.idea.compose.preview.animation

import com.android.SdkConstants
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.tabs.TabInfo
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AnimationTabsTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var parentDisposable: Disposable

  private lateinit var surface: DesignSurface<*>

  @Before
  fun setUp() {
    parentDisposable = Disposer.newDisposable()
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(
          projectRule,
          "layout",
          "layout.xml",
          ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)
        )
        .build()
    }
    surface = NlDesignSurface.builder(projectRule.project, parentDisposable).build()
    surface.addModelWithoutRender(model)
  }

  @After
  fun tearDown() {
    Disposer.dispose(parentDisposable)
  }

  @Test
  fun `create tab panel with navigation`() {
    invokeAndWaitIfNeeded {
      val tabs = AnimationTabs(surface)
      tabs.component.setSize(300, 300)
      assertNotNull(tabs.component)
      // First added tab should has key listeners for navigation.
      TabInfo(JPanel()).let {
        tabs.addTab(it)
        assertEquals(1, tabs.myInfo2Label[it]?.keyListeners?.size)
      }
      // Later added tabs also should have key listeners for navigation.
      TabInfo(JPanel()).let {
        tabs.addTab(it)
        assertEquals(1, tabs.myInfo2Label[it]?.keyListeners?.size)
      }
      TabInfo(JPanel()).let {
        tabs.addTab(it)
        assertEquals(1, tabs.myInfo2Label[it]?.keyListeners?.size)
      }
    }
  }

  @Test
  fun `open and close tabs`() {
    invokeAndWaitIfNeeded {
      val tabs = AnimationTabs(surface)
      tabs.component.setSize(300, 300)
      assertNotNull(tabs.component)

      // Add three tabs.
      var closedInfo: TabInfo? = null
      val firstInfo = TabInfo(JPanel()).also { tabs.addTabWithCloseButton(it) { closedInfo = it } }
      val secondInfo = TabInfo(JPanel()).let { tabs.addTabWithCloseButton(it) { closedInfo = it } }
      val thirdInfo = TabInfo(JPanel()).let { tabs.addTabWithCloseButton(it) { closedInfo = it } }
      val ui = FakeUi(tabs)

      // Close first tab.
      ui.clickOn(tabs.myInfo2Label[firstInfo]?.components?.get(1)!!)
      assertEquals(firstInfo, closedInfo)
      assertEquals(2, tabs.tabCount)

      // Close third tab.
      ui.clickOn(tabs.myInfo2Label[thirdInfo]?.components?.get(1)!!)
      assertEquals(thirdInfo, closedInfo)
      assertEquals(1, tabs.tabCount)

      // Close second tab.
      ui.clickOn(tabs.myInfo2Label[secondInfo]?.components?.get(1)!!)
      assertEquals(secondInfo, closedInfo)
      assertEquals(0, tabs.tabCount)
    }
  }
}
