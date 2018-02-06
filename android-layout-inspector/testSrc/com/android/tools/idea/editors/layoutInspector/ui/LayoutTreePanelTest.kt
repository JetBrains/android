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
package com.android.tools.idea.editors.layoutInspector.ui

import com.android.tools.idea.editors.layoutInspector.getTestFile
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import kotlin.test.assertFalse


/**
 * Test Sub-view feature in layout inspector
 */
class LayoutTreePanelTest : AndroidTestCase() {

  private lateinit var myContext: com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext
  private lateinit var myPanel: com.android.tools.idea.editors.layoutInspector.ui.LayoutTreePanel
  private lateinit var myTestData: com.android.tools.idea.editors.layoutInspector.LayoutFileData

  override fun setUp() {
    super.setUp()
    val file = getTestFile()
    myTestData = com.android.tools.idea.editors.layoutInspector.LayoutFileData(file)

    myContext = com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext(myTestData, project)
    myPanel = com.android.tools.idea.editors.layoutInspector.ui.LayoutTreePanel()
    myPanel.setSize(800, 800)
    myPanel.setToolContext(myContext)
  }

  //check back panel is not visible by default
  @Test
  fun testDefaultBackPanel() {
    assertFalse(myPanel.backPanel!!.isVisible)
  }

  //check back panel is visible after diving into a sub view
  // then back to invisible after it is removed.
  @Test
  fun testBackPanelVisible() {
    val subViewRoot = myContext.root!!.children[0]
    myContext.subviewList.add(subViewRoot)

    assertTrue(myPanel.backPanel!!.isVisible)
    assertEquals(myContext.subviewList.last(), subViewRoot)

    myContext.subviewList.remove(subViewRoot)
    assertFalse(myPanel.backPanel!!.isVisible)
  }
}
